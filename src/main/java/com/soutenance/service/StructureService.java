package com.soutenance.service;

import com.soutenance.dao.StructureDao;
import com.soutenance.dao.EmployeDao;
import com.soutenance.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class StructureService {

    @Autowired private StructureDao structureDao;
    @Autowired private EmployeDao   employeDao;
    @Autowired(required = false)
    private com.soutenance.dao.InstitutionDao institutionDao;

    public com.soutenance.entity.Institution getInstitutionById(Long id) {
        if (id == null || institutionDao == null) return null;
        try { return institutionDao.findById(id); } catch (Exception e) { return null; }
    }

    // ══════════════════════════════════════════════════════════
    //  DIRECTIONS
    // ══════════════════════════════════════════════════════════

    public List<Direction> getAllDirections() {
        return structureDao.findAllDirections();
    }

    /** CORRECTION : retourne uniquement les directions de l'institution */
    public List<Direction> getDirectionsByInstitution(Long institutionId) {
        if (institutionId == null) return structureDao.findAllDirections();
        return structureDao.findDirectionsByInstitution(institutionId);
    }

    public List<Division> getDivisionsByInstitution(Long institutionId) {
        if (institutionId == null) return structureDao.findAllDivisions();
        return structureDao.findDivisionsByInstitution(institutionId);
    }

    public List<com.soutenance.entity.Service> getServicesByInstitution(Long institutionId) {
        if (institutionId == null) return structureDao.findAllServices();
        return structureDao.findServicesByInstitution(institutionId);
    }

    public List<Bureau> getBureauxByInstitution(Long institutionId) {
        if (institutionId == null) return structureDao.findAllBureaux();
        return structureDao.findBureauxByInstitution(institutionId);
    }

    public List<Direction> getDirectionsActives() {
        return structureDao.findDirectionsActives();
    }

    public Direction getDirectionById(Long id) {
        return structureDao.findDirectionById(id);
    }

    public void saveDirection(Direction d, String username) {
        d.setCreePar(username);
        structureDao.saveDirection(d);
    }

    public void updateDirection(Direction d) {
        structureDao.updateDirection(d);
    }

    public void deleteDirection(Long id) {
        structureDao.deleteDirection(id);
    }

    public long countDirections() {
        return structureDao.countDirections();
    }

    public long countDivisionsByDirection(Long directionId) {
        return structureDao.countDivisionsByDirection(directionId);
    }

    // ══════════════════════════════════════════════════════════
    //  DIVISIONS
    // ══════════════════════════════════════════════════════════

    public List<Division> getAllDivisions() {
        return structureDao.findAllDivisions();
    }

    public List<Division> getDivisionsByDirection(Long directionId) {
        return structureDao.findDivisionsByDirection(directionId);
    }

    public Division getDivisionById(Long id) {
        return structureDao.findDivisionById(id);
    }

    public void saveDivision(Division d, String username) {
        d.setCreePar(username);
        structureDao.saveDivision(d);
    }

    public void updateDivision(Division d) {
        structureDao.updateDivision(d);
    }

    public void deleteDivision(Long id) {
        structureDao.deleteDivision(id);
    }

    public long countServicesByDivision(Long divisionId) {
        return structureDao.countServicesByDivision(divisionId);
    }

    // ══════════════════════════════════════════════════════════
    //  SERVICES
    // ══════════════════════════════════════════════════════════

    public List<com.soutenance.entity.Service> getAllServices() {
        return structureDao.findAllServices();
    }

    public List<com.soutenance.entity.Service> getServicesByDivision(Long divisionId) {
        return structureDao.findServicesByDivision(divisionId);
    }

    public com.soutenance.entity.Service getServiceById(Long id) {
        return structureDao.findServiceById(id);
    }

    public void saveService(com.soutenance.entity.Service s, String username) {
        s.setCreePar(username);
        structureDao.saveService(s);
    }

    public void updateService(com.soutenance.entity.Service s) {
        structureDao.updateService(s);
    }

    public void deleteService(Long id) {
        structureDao.deleteService(id);
    }

    public long countBureauxByService(Long serviceId) {
        return structureDao.countBureauxByService(serviceId);
    }

    // ══════════════════════════════════════════════════════════
    //  BUREAUX
    // ══════════════════════════════════════════════════════════

    public List<Bureau> getAllBureaux() {
        return structureDao.findAllBureaux();
    }

    public List<Bureau> getBureauxByService(Long serviceId) {
        return structureDao.findBureauxByService(serviceId);
    }

    public Bureau getBureauById(Long id) {
        return structureDao.findBureauById(id);
    }

    public void saveBureau(Bureau b, String username) {
        b.setCreePar(username);
        structureDao.saveBureau(b);
    }

    public void updateBureau(Bureau b) {
        structureDao.updateBureau(b);
    }

    public void deleteBureau(Long id) {
        structureDao.deleteBureau(id);
    }

    // ══════════════════════════════════════════════════════════
    //  EMPLOYES (pour sélection chef)
    // ══════════════════════════════════════════════════════════

    public List<Employe> getAllEmployes() {
        return employeDao.findAll();
    }

    // ══════════════════════════════════════════════════════════
    //  DÉPARTEMENTS (structure libre)
    // ══════════════════════════════════════════════════════════

    public List<Departement> getDepartementsByInstitution(Long institutionId) {
        return structureDao.findDepartementsByInstitution(institutionId);
    }

    public Departement getDepartementById(Long id) {
        return structureDao.findDepartementById(id);
    }

    public void saveDepartement(Departement d, String username) {
        d.setCreePar(username);
        structureDao.saveDepartement(d);
    }

    public void updateDepartement(Departement d) {
        structureDao.updateDepartement(d);
    }

    public void deleteDepartement(Long id) {
        structureDao.deleteDepartement(id);
    }

    public long countServicesByDepartement(Long departementId) {
        return structureDao.countServicesByDepartement(departementId);
    }
}