/*
 * JuiceFS, Copyright 2021 Juicedata, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.juicefs.utils;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.ClassDefinition;

public class PatchUtil {
  private static final Logger LOG = LoggerFactory.getLogger(PatchUtil.class);

  public enum PatchType {
    BODY, BEFORE, AFTER
  }

  public static class ClassMethod {
    private String method;
    private String[] params;
    private PatchType[] types;
    private String[] codes;

    public ClassMethod(String method, String[] params, String[] codes, PatchType[] types) {
      if (codes.length != types.length) {
        LOG.error("{} has {} codes, but only {} types", method, codes.length, types.length);
      }
      this.method = method;
      this.params = params;
      this.codes = codes;
      this.types = types;
    }
  }

  public static synchronized void doPatch(String className, ClassMethod[] classMethods) {

    ClassPool classPool = ClassPool.getDefault();
    try {
      CtClass cls = classPool.get(className);

      for (ClassMethod classMethod : classMethods) {
        String method = classMethod.method;

        CtMethod mtd;
        String[] params = classMethod.params;
        if (params != null) {
          CtClass[] cts = new CtClass[params.length];
          for (int i = 0; i < params.length; i++) {
            cts[i] = classPool.get(params[i]);
          }
          mtd = cls.getDeclaredMethod(method, cts);
        } else {
          mtd = cls.getDeclaredMethod(method);
        }

        String[] codes = classMethod.codes;
        PatchType[] types = classMethod.types;
        for (int i = 0; i < codes.length; i++) {
          switch (types[i]) {
            case BODY:
              mtd.setBody(codes[0]);
              break;
            case AFTER:
              mtd.insertAfter(codes[0], true);
              break;
            case BEFORE:
              mtd.insertBefore(codes[0]);
              break;
          }
        }
      }

      RedefineClassAgent.redefineClasses(new ClassDefinition(Class.forName(className), cls.toBytecode()));
      cls.detach();
    } catch (NotFoundException | NoClassDefFoundError ignored) {
    } catch (Throwable e) {
      LOG.warn(String.format("patch %s failed", className), e);
    }
  }

  public static void patchBody(String className, String method, String[] params, String code) {
    doPatch(className, new ClassMethod[]{new ClassMethod(method, params, new String[]{code}, new PatchType[]{PatchType.BODY})});
  }

  public static void patchBefore(String className, String method, String[] params, String code) {
    doPatch(className, new ClassMethod[]{new ClassMethod(method, params, new String[]{code}, new PatchType[]{PatchType.BEFORE})});
  }

  public static void patchAfter(String className, String method, String[] params, String code) {
    doPatch(className, new ClassMethod[]{new ClassMethod(method, params, new String[]{code}, new PatchType[]{PatchType.AFTER})});
  }

  public static void patchBeforeAndAfter(String className, String method, String[] params, String beforeCode, String afterCode) {
    doPatch(className, new ClassMethod[]{new ClassMethod(method, params, new String[]{beforeCode, afterCode}, new PatchType[]{PatchType.BEFORE, PatchType.AFTER})});
  }

}
