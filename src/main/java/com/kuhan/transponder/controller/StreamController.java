package com.kuhan.transponder.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController("/transponder")
public class StreamController {

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.getMessageConverters().add(new ProtobufHttpMessageConverter());
        return rt;
    }

    @Autowired
    RestTemplate restTemplate;

    @GetMapping("/stream/{id}")
    public String openStream(@PathVariable("id") int id){

        return "";
    }

    @PostMapping("/stream")
    public String createStream(){
        return "";
    }

}
