package com.fly.demo.service.impl;

import com.fly.demo.service.DemoService;
import com.fly.mvcframework.annotation.FlyService;

@FlyService
public class DemoServiceImpl implements DemoService {

    @Override
    public String query(String name) {
        return "test " + name;
    }

}
