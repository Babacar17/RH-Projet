package com.soutenance.configuration;

import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration Spring principale.
 *
 * CORRECTION : ajout du bean BCryptPasswordEncoder partagé.
 *
 * Avant cette correction, trois classes instanciaient leur propre encodeur :
 *   - UserService    : private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder()
 *   - AdminService   : private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder()
 *   - CandidatService: private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder()
 *
 * Problèmes :
 *   1. Si le strength BCrypt change dans le futur, seul ce @Bean serait à modifier.
 *   2. MonCompteBean injectait déjà @Autowired BCryptPasswordEncoder — il ne fonctionnait
 *      que parce qu'aucun autre bean du même type n'existait. Maintenant le bean est déclaré
 *      explicitement, la dépendance est claire et documentée.
 *   3. Une seule instance est créée au démarrage (singleton Spring) plutôt que N instances,
 *      ce qui économise légèrement les ressources.
 *
 * Les classes UserService, AdminService et CandidatService injectent maintenant ce bean
 * via @Autowired au lieu de l'instancier manuellement.
 *
 * @EnableScheduling — OBLIGATOIRE pour que @Scheduled fonctionne.
 * Sans cette annotation, le scheduler OffreEmploiScheduler ne démarre jamais.
 */
@Configuration
@EnableScheduling
public class SpringJsfConfig {

    /**
     * Encodeur BCrypt partagé par tous les services de l'application.
     * Strength par défaut = 10 (recommandation OWASP pour une authentification
     * web standard). Augmenter à 12 en production si les serveurs le permettent.
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Enregistrement du scope "view" JSF pour Spring.
     * Permet d'utiliser @Scope("view") sur les beans JSF.
     * Le bean doit être static pour s'exécuter tôt dans le cycle de vie Spring.
     */
    @Bean
    public static CustomScopeConfigurer customScopeConfigurer() {
        CustomScopeConfigurer configurer = new CustomScopeConfigurer();
        Map<String, Object> scopes = new HashMap<>();
        scopes.put("view", new ViewScope());
        configurer.setScopes(scopes);
        return configurer;
    }
}