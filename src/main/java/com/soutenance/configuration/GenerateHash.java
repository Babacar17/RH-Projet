package com.soutenance.configuration;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerateHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        String motDePasse = "Admin@123";
        String hash = encoder.encode(motDePasse);
        
        System.out.println("Hash généré : " + hash);
        
        // Tester la vérification
        boolean match = encoder.matches(motDePasse, hash);
        System.out.println("Vérification : " + match);
        
        // Tester avec le hash en base
        String hashEnBase = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LnM.oJ3VBi2";
        boolean matchBase = encoder.matches(motDePasse, hashEnBase);
        System.out.println("Match avec hash SQL : " + matchBase);
    }
}