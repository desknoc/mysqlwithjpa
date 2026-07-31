package com.sena.mysqlwithjpa;

import org.springframework.boot.SpringApplication;

public class TestMysqlwithjpaApplication {

	public static void main(String[] args) {
		SpringApplication.from(MysqlwithjpaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
