package com.tecnopc.admin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling
public class TecnoPcApplication { public static void main(String[] args) { SpringApplication.run(TecnoPcApplication.class,args); } }
