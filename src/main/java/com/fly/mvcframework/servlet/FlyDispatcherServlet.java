package com.fly.mvcframework.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fly.mvcframework.annotation.FlyAutowried;
import com.fly.mvcframework.annotation.FlyController;
import com.fly.mvcframework.annotation.FlyRequestMapping;
import com.fly.mvcframework.annotation.FlyService;

public class FlyDispatcherServlet extends HttpServlet {

    private Properties          properties     = new Properties();
    private List<String>        classNames     = new ArrayList<>();
    // IOC容器
    private Map<String, Object> ioc            = new HashMap<>();

    // private Map<String, Method> handlerMapping = new HashMap<>();

    private List<Handler>       handlerMapping = new ArrayList<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        // 2、扫描所有相关类
        doScanner(properties.getProperty("scanPackage"));
        // 3、初始化相关实例并保存到IOC容器里
        doInstance();
        // 4、自动化依赖注入
        doAutowired();
        // 5、初始化handlerMapping
        initHandlerMapping();
        System.out.println("Fly MVC Framework inited");
    }

    private void doLoadConfig(String location) {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(location);
        try {
            properties.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != inputStream) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doScanner(String packageName) {
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File classesDir = new File(url.getFile());
        for (File file : classesDir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(packageName + "." + file.getName());
            } else {
                String className = packageName + "." + file.getName().replace(".class", "");
                classNames.add(className);
            }
        }

    }

    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                // 进行实例化
                // 有conroller service注解的才是spring的bean
                if (clazz.isAnnotationPresent(FlyController.class)) {
                    String beanName = lowerFirst(clazz.getSimpleName());
                    ioc.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(FlyService.class)) {
                    // beanName beanId
                    // 1、默认采用类名首字母小写
                    // 2、如果自定义bean的名字优先使用自定义名字
                    // 3、根据类型匹配，利用实现类的接口作为key
                    FlyService service = clazz.getAnnotation(FlyService.class);
                    String beanName = service.value();
                    if ("".equals(beanName.trim())) {
                        beanName = lowerFirst(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        ioc.put(i.getName(), instance);
                    }

                } else {
                    continue;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doAutowired() {

        if (ioc.isEmpty()) {
            return;
        }
        for (Entry<String, Object> entry : ioc.entrySet()) {

            // 反射获取bean里的所有属性filed
            Field[] fileds = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fileds) {
                if (field.isAnnotationPresent(FlyAutowried.class)) {
                    FlyAutowried autowried = field.getAnnotation(FlyAutowried.class);
                    String beanName = autowried.value().trim();
                    if ("".equals(beanName)) {
                        beanName = field.getType().getName();
                    }
                    field.setAccessible(true);// 强制可访问 无论private还是public
                    try {
                        field.set(entry.getValue(), ioc.get(beanName));// set注入
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }
                }
            }
        }
    }

    private void initHandlerMapping() {

        if (ioc.isEmpty()) {
            return;
        }
        for (Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(FlyController.class)) {
                continue;
            }

            String url = "";
            // 类头上的RequestMapping 注解
            if (clazz.isAnnotationPresent(FlyRequestMapping.class)) {
                FlyRequestMapping requestMapping = clazz.getAnnotation(FlyRequestMapping.class);
                url = requestMapping.value();
            }
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(FlyRequestMapping.class)) {
                    FlyRequestMapping requestMapping = method.getAnnotation(FlyRequestMapping.class);
                    String regex = (url + requestMapping.value()).replaceAll("/+", "/");
                    Pattern pattern = Pattern.compile(regex);
                    handlerMapping.add(new Handler(entry.getValue(), method, pattern));
                    System.out.println("Mapping ： " + regex + " " + method);
                }
            }

        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        Handler handler = getHandler(req);
        if (handler == null) {
            resp.getWriter().println("404 Not Found!");
            return;
        }
        // 获取方法参数列表
        Class<?>[] paramTypes = handler.method.getParameterTypes();
        // 保存所有需要自动复制的参数值
        Object[] paramValues = new Object[paramTypes.length];
        Map<String, String[]> params = req.getParameterMap();
        for (Entry<String, String[]> param : params.entrySet()) {
            String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
            // 如果找到匹配对象，则开始填充参数值
            if (!handler.paremIndexMapping.containsKey(param.getKey())) {
                continue;
            }

            int index = handler.paremIndexMapping.get(param.getKey());
            paramValues[index] = convert(paramTypes[index], value);
        }

        // 设置方法中request和response对象
        int reqIndex = handler.paremIndexMapping.get(HttpServletRequest.class.getName());
        paramValues[reqIndex] = req;
        int respIndex = handler.paremIndexMapping.get(HttpServletResponse.class.getName());
        paramValues[respIndex] = resp;
        handler.method.invoke(handler.controller, paramValues);

    }

    /**
     * 首字母大写转小写
     * 
     * @param str
     * @return
     */
    private String lowerFirst(String str) {
        char[] chars = str.toCharArray();
        if (chars[0] >= 65 && chars[0] <= 90) {
            chars[0] += 32;
        }
        return String.valueOf(chars);
    }

    private Handler getHandler(HttpServletRequest req) {
        if (handlerMapping.isEmpty()) {
            return null;
        }
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        String regex = url.replace(contextPath, "").replaceAll("/+", "/");
        for (Handler handler : handlerMapping) {
            Matcher marcher = handler.pattern.matcher(regex);
            if (!marcher.matches()) {
                continue;
            }
            return handler;
        }
        return null;
    }

    private Object convert(Class<?> type, String value) {
        if (Integer.class == type) {
            return Integer.valueOf(value);
        }
        return value;
    }

}
