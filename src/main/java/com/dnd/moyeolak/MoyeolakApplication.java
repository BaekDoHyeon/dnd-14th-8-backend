package com.dnd.moyeolak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// JPA 감사는 JpaAuditingConfig(@EnableJpaAuditing, KST DateTimeProvider)에서 활성화한다.
@EnableScheduling
@SpringBootApplication
public class MoyeolakApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoyeolakApplication.class, args);
    }

}
