package com.fly.demo.action;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fly.demo.service.DemoService;
import com.fly.mvcframework.annotation.FlyAutowried;
import com.fly.mvcframework.annotation.FlyController;
import com.fly.mvcframework.annotation.FlyRequestMapping;
import com.fly.mvcframework.annotation.FlyRequestParam;

@FlyController
public class TestController {

    @FlyAutowried
    private DemoService demoService;

    
    //http://localhost:8080/flymvc/test?name=tom
    @FlyRequestMapping("/test")
    public void query(HttpServletRequest req, HttpServletResponse resp, @FlyRequestParam("name") String name) {
        String result= demoService.query(name);
        
        try {
            resp.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
