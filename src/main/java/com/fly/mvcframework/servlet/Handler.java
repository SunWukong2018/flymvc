package com.fly.mvcframework.servlet;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fly.mvcframework.annotation.FlyRequestParam;

public class Handler {

    protected Object               controller;       // 保存方法对应的实例
    protected Method               method;           // 保存映射的方法
    protected Pattern              pattern;
    protected Map<String, Integer> paremIndexMapping;// 参数顺序

    public Handler(Object controller, Method method, Pattern pattern){

        this.controller = controller;
        this.method = method;
        this.pattern = pattern;
        paremIndexMapping = new HashMap<>();
        putParemIndexMapping(method);
    }

    private void putParemIndexMapping(Method method) {
        // 提取方法中加入注解参数
        Annotation[][] pa = method.getParameterAnnotations();
        for (int i = 0; i < pa.length; i++) {
            for (Annotation a : pa[i]) {
                if (a instanceof FlyRequestParam) {
                    String paramName = ((FlyRequestParam) a).value();
                    if (!"".equals(paramName.trim())) {
                        paremIndexMapping.put(paramName, i);
                    }
                }
            }
        }

        // 提取方法中request和response参数
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> type = paramTypes[i];
            if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                paremIndexMapping.put(type.getName(), i);
            }
        }
    }
    


}
