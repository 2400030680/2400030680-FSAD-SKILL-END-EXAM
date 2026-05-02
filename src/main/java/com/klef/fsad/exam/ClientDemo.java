package com.klef.fsad.exam;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

public class ClientDemo {

    public static void main(String[] args) {
        Configuration configuration = new Configuration();
        configuration.configure("hibernate.cfg.xml");

        SessionFactory sessionFactory = configuration.buildSessionFactory();

        int departmentId = insertDepartment(sessionFactory);
        deleteDepartment(sessionFactory, departmentId);

        sessionFactory.close();
    }

    private static int insertDepartment(SessionFactory sessionFactory) {
        Session session = sessionFactory.openSession();
        Transaction transaction = session.beginTransaction();

        Department department = new Department();
        department.setName("Computer Science");
        department.setDescription("CSE Department");
        department.setDate("2026-05-02");
        department.setStatus("Active");

        session.persist(department);
        transaction.commit();

        System.out.println("Department inserted successfully with ID: " + department.getId());
        session.close();

        return department.getId();
    }

    private static void deleteDepartment(SessionFactory sessionFactory, int departmentId) {
        Session session = sessionFactory.openSession();
        Transaction transaction = session.beginTransaction();

        Department department = session.get(Department.class, departmentId);

        if (department != null) {
            session.remove(department);
            System.out.println("Department deleted successfully with ID: " + departmentId);
        } else {
            System.out.println("Department not found with ID: " + departmentId);
        }

        transaction.commit();
        session.close();
    }
}
