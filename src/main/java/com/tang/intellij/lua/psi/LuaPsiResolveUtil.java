/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.psi;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.tang.intellij.lua.Constants;
import com.tang.intellij.lua.comment.psi.LuaDocParamDef;
import com.tang.intellij.lua.comment.psi.api.LuaComment;
import com.tang.intellij.lua.lang.GuessTypeKind;
import com.tang.intellij.lua.lang.type.LuaType;
import com.tang.intellij.lua.lang.type.LuaTypeSet;
import com.tang.intellij.lua.reference.LuaReference;
import com.tang.intellij.lua.search.SearchContext;
import com.tang.intellij.lua.stubs.index.LuaClassIndex;
import com.tang.intellij.lua.stubs.index.LuaGlobalFuncIndex;
import com.tang.intellij.lua.stubs.index.LuaGlobalVarIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 *
 * Created by TangZX on 2016/11/24.
 */
public class LuaPsiResolveUtil {

    static LuaFuncBodyOwner resolveFuncBodyOwner(@NotNull LuaNameExpr ref, SearchContext context) {
        final Ref<LuaFuncBodyOwner> ret = Ref.create();
        String refName = ref.getName();
        //local 函数名
        LuaPsiTreeUtil.walkUpLocalFuncDef(ref, localFuncDef -> {
            if (refName.equals(localFuncDef.getName())) {
                ret.set(localFuncDef);
                return false;
            }
            return true;
        });

        //global function
        if (ret.isNull()) {
            ret.set(LuaGlobalFuncIndex.find(refName, context));
        }

        return ret.get();
    }

    public static PsiElement resolveLocal(LuaNameExpr ref, SearchContext context) {
        final Ref<PsiElement> ret = Ref.create();
        String refName = ref.getName();

        if (refName.equals(Constants.WORD_SELF)) {
            LuaBlock block = PsiTreeUtil.getParentOfType(ref, LuaBlock.class);
            if (block != null) {
                LuaClassMethodDef classMethodFuncDef = PsiTreeUtil.getParentOfType(block, LuaClassMethodDef.class);
                if (classMethodFuncDef != null && !classMethodFuncDef.isStatic()) {
                    LuaExpr expr = classMethodFuncDef.getClassMethodName().getExpr();
                    PsiReference reference = expr.getReference();
                    if (reference instanceof LuaReference) {
                        PsiElement resolve = ((LuaReference) reference).resolve(context);
                        ret.set(resolve);
                    }
                    if (ret.isNull() && expr instanceof LuaNameExpr)
                        ret.set(expr);
                }
            }
        }

        //local 变量, 参数
        if (ret.isNull()) {
            LuaPsiTreeUtil.walkUpLocalNameDef(ref, nameDef -> {
                if (refName.equals(nameDef.getName())) {
                    ret.set(nameDef);
                    return false;
                }
                return true;
            });
        }

        //local 函数名
        if (ret.isNull()) {
            LuaPsiTreeUtil.walkUpLocalFuncDef(ref, nameDef -> {
                String name= nameDef.getName();
                if (refName.equals(name)) {
                    ret.set(nameDef);
                    return false;
                }
                return true;
            });
        }

        return ret.get();
    }

    public static boolean isUpValue(@NotNull LuaNameExpr ref, @NotNull SearchContext context) {
        LuaFuncBody funcBody = PsiTreeUtil.getParentOfType(ref, LuaFuncBody.class);
        if (funcBody == null)
            return false;

        String refName = ref.getName();
        if (refName.equals(Constants.WORD_SELF)) {
            LuaClassMethodDef classMethodFuncDef = PsiTreeUtil.getParentOfType(ref, LuaClassMethodDef.class);
            if (classMethodFuncDef != null && !classMethodFuncDef.isStatic()) {
                LuaFuncBody methodFuncBody = classMethodFuncDef.getFuncBody();
                if (methodFuncBody != null)
                    return methodFuncBody.getTextOffset() < funcBody.getTextOffset();
            }
        }

        PsiElement resolve = resolveLocal(ref, context);
        if (resolve != null) {
            if (!funcBody.getTextRange().contains(resolve.getTextRange()))
                return true;
        }

        return false;
    }

    /**
     * 查找这个引用
     * @param ref 要查找的ref
     * @param context context
     * @return PsiElement
     */
    public static PsiElement resolve(LuaNameExpr ref, SearchContext context) {
        //search local
        PsiElement resolveResult = resolveLocal(ref, context);

        String refName = ref.getName();
        //global field
        if (resolveResult == null) {
            LuaGlobalVar globalVar = LuaGlobalVarIndex.find(refName, context);
            if (globalVar != null) {
                resolveResult = globalVar.getNameRef();
            }
        }

        //global function
        if (resolveResult == null) {
            resolveResult = LuaGlobalFuncIndex.find(refName, context);
        }

        return resolveResult;
    }

    @NotNull
    public static PsiElement[] multiResolve(LuaNameExpr ref, SearchContext context) {
        SmartList<PsiElement> list = new SmartList<>();
        //search local
        PsiElement resolveResult = resolveLocal(ref, context);
        if (resolveResult != null) {
            list.add(resolveResult);
        } else {
            String refName = ref.getName();
            //global field
            Collection<LuaGlobalVar> globalVars = LuaGlobalVarIndex.findAll(refName, context);
            for (LuaGlobalVar globalVar : globalVars) {
                list.add(globalVar.getNameRef());
            }

            //global function
            Collection<LuaGlobalFuncDef> globalFuns = LuaGlobalFuncIndex.findAll(refName, context);
            list.addAll(globalFuns);
        }
        return list.toArray(new PsiElement[list.size()]);
    }

    public static PsiElement resolve(LuaIndexExpr indexExpr, SearchContext context) {
        PsiElement id = indexExpr.getId();
        if (id == null)
            return null;

        LuaTypeSet typeSet = indexExpr.guessPrefixType(context);
        if (typeSet != null) {
            String idString = id.getText();
            for (LuaType type : typeSet.getTypes()) {
                //属性
                LuaClassField fieldDef = type.findField(idString, context);
                if (fieldDef != null)
                    return fieldDef;
                //方法
                LuaClassMethodDef methodDef = type.findMethod(idString, context);
                if (methodDef != null)
                    return methodDef;
            }
        }
        return null;
    }

    @Nullable
    static LuaTypeSet resolveType(LuaNameDef nameDef, SearchContext context) {
        LuaTypeSet typeSet = null;
        //作为函数参数，类型在函数注释里找
        if (nameDef instanceof LuaParamNameDef) {
            typeSet = resolveParamType((LuaParamNameDef) nameDef, context);
        }
        //在Table字段里
        else if (nameDef.getParent() instanceof LuaTableField) {
            LuaTableField field = (LuaTableField) nameDef.getParent();
            LuaExpr expr = PsiTreeUtil.findChildOfType(field, LuaExpr.class);
            if (expr != null) typeSet = expr.guessType(context);
        }
        //变量声明，local x = 0
        else {
            LuaLocalDef localDef = PsiTreeUtil.getParentOfType(nameDef, LuaLocalDef.class);
            if (localDef != null) {
                LuaComment comment = localDef.getComment();
                if (comment != null) {
                    typeSet = comment.guessType(context);
                }

                //计算 expr 返回类型
                if (typeSet == null || typeSet.isEmpty()) {
                    LuaNameList nameList = localDef.getNameList();
                    LuaExprList exprList = localDef.getExprList();
                    if (nameList != null && exprList != null) {
                        int index = nameList.getNameDefList().indexOf(nameDef);
                        if (index != -1) {
                            List<LuaExpr> exprs = exprList.getExprList();
                            if (index < exprs.size()) {
                                LuaExpr expr = exprs.get(index);
                                typeSet = expr.guessType(context);
                            }
                        }
                    }
                }

                //anonymous
                if (typeSet == null ||typeSet.isEmpty())
                    typeSet = LuaTypeSet.create(LuaType.createAnonymousType(nameDef));
            }
        }

        if (typeSet != null) {
            if (context.isGuessTypeKind(GuessTypeKind.FromName)) {
                String str = nameDef.getText();
                if (str.length() > 2) {
                    final LuaTypeSet set = typeSet;
                    CamelHumpMatcher matcher = new CamelHumpMatcher(str, false);
                    LuaClassIndex.getInstance().processAllKeys(context.getProject(), (cls) -> {
                        if (matcher.prefixMatches(cls)) {
                            LuaType type = LuaType.create(cls, null);
                            type.setUnreliable(true);
                            set.addType(type);
                        }
                        return true;
                    });
                }
            }
        }
        return typeSet;
    }

    /**
     * 找参数的类型
     * @param paramNameDef param name
     * @param context SearchContext
     * @return LuaTypeSet
     */
    private static LuaTypeSet resolveParamType(LuaParamNameDef paramNameDef, SearchContext context) {
        LuaCommentOwner owner = PsiTreeUtil.getParentOfType(paramNameDef, LuaCommentOwner.class);
        if (owner != null) {
            String paramName = paramNameDef.getText();
            LuaComment comment = owner.getComment();
            if (comment != null) {
                LuaDocParamDef paramDef = comment.getParamDef(paramName);
                if (paramDef != null) {
                    return paramDef.guessType(context);
                }
            }

            // 如果是个类方法，则有可能在父类里
            if (owner instanceof LuaClassMethodDef) {
                LuaClassMethodDef classMethodDef = (LuaClassMethodDef) owner;
                LuaType classType = classMethodDef.getClassType(context);
                String methodName = classMethodDef.getName();
                while (classType != null){
                    classType = classType.getSuperClass(context);
                    if (classType != null) {
                        LuaClassMethodDef superMethod = classType.findMethod(methodName, context);
                        if (superMethod != null) {
                            LuaParamInfo[] params = superMethod.getParams();//todo : 优化
                            for (LuaParamInfo param : params) {
                                if (paramName.equals(param.getName())) {
                                    String[] types = param.getTypes();
                                    if (types.length > 0) {
                                        LuaTypeSet set = LuaTypeSet.create();
                                        for (String type : types) {
                                            set.addType(LuaType.create(type, null));
                                        }
                                        return set;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 找到 require 的文件路径
     * @param pathString 参数字符串 require "aa.bb.cc"
     * @param project MyProject
     * @return PsiFile
     */
    public static LuaFile resolveRequireFile(String pathString, Project project) {
        if (pathString == null)
            return null;
        String fileName = pathString.replace('.', '/');
        VirtualFile f = LuaFileUtil.findFile(project, fileName);
        if (f != null) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(f);
            if (psiFile instanceof LuaFile)
                return (LuaFile) psiFile;
        }
        return null;
    }

    public static String getAnonymousType(PsiElement element) {
        String fileName = element.getContainingFile().getName();
        int startOffset = element.getNode().getStartOffset();

        return String.format("%s@(%d)%s", fileName, startOffset, element.getText());
    }
}
