// This is a generated file. Not intended for manual editing.
package com.tang.intellij.lua.comment.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.tang.intellij.lua.lang.type.LuaType;
import com.tang.intellij.lua.search.SearchContext;

public interface LuaDocClassNameRef extends LuaDocPsiElement {

  @NotNull
  PsiElement getId();

  @NotNull
  PsiReference getReference();

  LuaType resolveType(SearchContext context);

}
