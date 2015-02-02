package com.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.model.Department;
import com.model.Employee;

public class DB {

	static Map<Integer, Employee> db = new HashMap<Integer, Employee>();
	static {
		Employee emp1 = new Employee();
		emp1.setId(1);
		emp1.setFirstName("Peter");
		emp1.setLastName("Bratsh");
		Department dept1 = new Department();
		dept1.setDeptId(1111);
		dept1.setDeptName("dept1");
		emp1.setDept(dept1);

		Employee emp2 = new Employee();
		emp2.setId(2);
		emp2.setFirstName("John");
		emp2.setLastName("Williams");
		Department dept2 = new Department();
		dept2.setDeptId(2222);
		dept2.setDeptName("dept2");
		emp2.setDept(dept2);

		db.put(1, emp1);
		db.put(2, emp2);

	}

	public static Employee getEmployee(int id) {
		return db.get(id);
	}

	public static List<Employee> getAllEmployees() {
		return new ArrayList<Employee>(db.values());
	}
}