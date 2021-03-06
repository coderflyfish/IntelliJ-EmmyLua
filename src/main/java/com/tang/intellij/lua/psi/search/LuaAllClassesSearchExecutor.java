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

package com.tang.intellij.lua.psi.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.util.Processor;
import com.tang.intellij.lua.lang.type.LuaType;
import com.tang.intellij.lua.stubs.index.LuaClassIndex;
import org.jetbrains.annotations.NotNull;

/**
 *
 * Created by tangzx on 2017/3/29.
 */
public class LuaAllClassesSearchExecutor extends QueryExecutorBase<LuaType, LuaAllClassesSearch.SearchParameters> {
    @Override
    public void processQuery(@NotNull LuaAllClassesSearch.SearchParameters searchParameters, @NotNull Processor<LuaType> processor) {
        DumbService.getInstance(searchParameters.getProject()).runReadActionInSmartMode(() -> {
            LuaClassIndex.getInstance().processAllKeys(searchParameters.getProject(), typeName -> {
                return processor.process(LuaType.create(typeName, null));
            });
        });
    }
}
