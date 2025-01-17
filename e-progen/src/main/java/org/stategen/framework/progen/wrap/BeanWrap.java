/*
 * Copyright (C) 2018  niaoge<78493244@qq.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.stategen.framework.progen.wrap;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stategen.framework.annotation.GenBean;
import org.stategen.framework.progen.GenContext;
import org.stategen.framework.progen.GenericTypeResolver;
import org.stategen.framework.progen.NamedContext;
import org.stategen.framework.progen.WrapContainer;
import org.stategen.framework.util.AnnotationUtil;
import org.stategen.framework.util.CollectionUtil;
import org.stategen.framework.util.ReflectionUtil;
import org.stategen.framework.util.StringUtil;

/**
 * The Class BeanWrap.
 */
public class BeanWrap extends BaseHasImportsWrap implements CanbeImportWrap {
    final static Logger logger = LoggerFactory.getLogger(BeanWrap.class);

    private BeanWrap parentBean;

    private String idKeyName;

    private Map<String, FieldWrap> genericFieldMap;

    private Map<String, FieldWrap> fieldMap = new LinkedHashMap<String, FieldWrap>();
    private Map<String, FieldWrap> allFieldMap = new LinkedHashMap<String, FieldWrap>();
    private Map<String, FieldWrap> superFieldMap = new LinkedHashMap<String, FieldWrap>();

    private Boolean genBean;

    private Boolean isGeneric;

    private FieldWrap generic;

    @Override
    public String getImportPath() {
        return CanbeImportWrap.super.getImportPath();
    }

    @Override
    public Boolean getIsGeneric() {
        return CollectionUtil.isNotEmpty(genericFieldMap);
    }

    private static boolean isTransient(AnnotatedElement annotatedElement) {
        int modifiers = ((Member) annotatedElement).getModifiers();
        if (Modifier.isTransient(modifiers)) {
            return true;
        }

        return false;
    }

    @Override
    public void setClazz(Class<?> clz) {
        super.setClazz(clz);
        this.scanAndAddFieldsClz(GenContext.wrapContainer);
    }

    private void scanAndAddFieldsClz(WrapContainer wrapContainer) {
        Class<?> currentType = getClazz();
        Map<String, Field> fieldNameFieldMap = ReflectionUtil.getFieldNameFieldMap(currentType);
        Map<String, Method> getterNameMethods = ReflectionUtil.getGetterNameMethods(currentType);
        Map<String, Method> getterNameMethodsSorted = new LinkedHashMap<String, Method>(getterNameMethods.size());

        //先按 field顺序排，再按getter方法排
        for (String fieldName : fieldNameFieldMap.keySet()) {
            Method method = getterNameMethods.remove(fieldName);
            if (method != null) {
                getterNameMethodsSorted.put(fieldName, method);
            }
        }

        getterNameMethodsSorted.putAll(getterNameMethods);
        Map<String, Parameter> fieldNameParameterMap = CollectionUtil.newEmptyMap();

        for (Entry<String, Method> entry : getterNameMethodsSorted.entrySet()) {

            Method getterMethod = entry.getValue();
            if (isTransient(getterMethod)) {
                continue;
            }

            String fieldName = entry.getKey();
            Field field = fieldNameFieldMap.get(fieldName);
            if (field != null && isTransient(field)) {
                continue;
            }

            Class<?> returnType = getterMethod.getReturnType();
            Type genericReturnType = getterMethod.getGenericReturnType();
            NamedContext context = new NamedContext(fieldNameParameterMap, fieldNameFieldMap, getterNameMethodsSorted);
            FieldWrap fieldWrap = new FieldWrap(context);
            GenContext.wrapContainer.genMemberWrap(null, returnType, genericReturnType, fieldWrap, getterMethod);
            fieldWrap.setOwner(this);

            fieldWrap.setMember(getterMethod);
            fieldWrap.setField(field);
            fieldWrap.setName(fieldName);

            addFieldWrap(fieldName, fieldWrap);
            String genericName = GenericTypeResolver.getGenericName(genericReturnType, 0);
            if (StringUtil.isNotEmpty(genericName)) {
                fieldWrap.setGenericName(genericName);
                this.addGenericFieldWrap(genericName, fieldWrap);
            }
            if (fieldWrap.getIsId()) {
                this.idKeyName = fieldName;
            }
        }
    }

    public List<FieldWrap> getGenericFields() {
        if (CollectionUtil.isNotEmpty(genericFieldMap)) {
            return new ArrayList<FieldWrap>(genericFieldMap.values());
        }
        return null;
    }

    public FieldWrap getGeneric() {
        if (isGeneric == null) {
            if (CollectionUtil.isNotEmpty(genericFieldMap)) {
                for (FieldWrap fieldWrap : genericFieldMap.values()) {
                    BaseWrap generic = null;
                    if (fieldWrap.getIsGeneric()) {
                        generic = fieldWrap;
                    } else {
                        generic = fieldWrap.getGeneric();
                    }
                    if (generic != null && generic.getIsObjectClass()) {
                        isGeneric = true;
                        this.generic = fieldWrap;
                        break;
                    }
                }
            }
            if (isGeneric == null) {
                isGeneric = false;
            }
        }
        return this.generic;
    }

    public Boolean isGeneric() {
        getGeneric();
        return isGeneric;
    }

    private void addGenericFieldWrap(String genericName, FieldWrap fieldWrap) {
        if (this.genericFieldMap == null) {
            this.genericFieldMap = new LinkedHashMap<String, FieldWrap>();
        }
        genericFieldMap.put(genericName, fieldWrap);
    }

    public Map<String, FieldWrap> getFieldWrapMap() {
        return fieldMap;
    }

    public Map<String, FieldWrap> getAllFieldMap() {
        return allFieldMap;
    }

    public void addFieldWrap(String fieldName, FieldWrap fieldWrap) {
        this.fieldMap.put(fieldName, fieldWrap);
        this.allFieldMap.put(fieldName, fieldWrap);
    }

    public FieldWrap get(String fieldName) {
        return allFieldMap.get(fieldName);
    }

    public List<FieldWrap> getFields() {
        return new ArrayList<FieldWrap>(fieldMap.values());
    }

    public List<FieldWrap> getAllFields() {
        return new ArrayList<FieldWrap>(allFieldMap.values());
    }

    public List<FieldWrap> getSuperFields() {
        return new ArrayList<FieldWrap>(superFieldMap.values());
    }

    public void setParentBean(BeanWrap parentBean) {
        this.parentBean = parentBean;
        if (parentBean != null) {
            addImport(parentBean);

            //把与父类相同的fieldName去掉
            Map<String, FieldWrap> parentFieldWrapMap = parentBean.getFieldWrapMap();
            Set<String> parentFieldNams = parentFieldWrapMap.keySet();
            for (String parentFieldName : parentFieldNams) {
                FieldWrap parentField = fieldMap.remove(parentFieldName);
                parentField.setIsSuper(true);
                superFieldMap.put(parentFieldName, parentField);
            }
        }
    }

    public BeanWrap getParentBean() {
        return parentBean;
    }

    public Boolean getExtend() {
        return parentBean != null;
    }

    public String getIdKeyName() {
        return idKeyName;
    }

    public Boolean getGenBean() {
        if (genBean == null) {
            //只在标注genForm(false)才不会生成，否则都生成
            genBean = AnnotationUtil.getAnnotationValueFormMembers(GenBean.class, GenBean::value, true, getClazz());
        }
        return genBean;
    }

    public Boolean getIsBean() {
        return true;
    }
}
