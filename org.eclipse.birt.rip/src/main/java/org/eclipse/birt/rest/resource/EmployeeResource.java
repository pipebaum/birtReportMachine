package org.eclipse.birt.rest.resource;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.db.DB;
import com.model.Employee;

@Path("/EmployeeResource")
public class EmployeeResource {

	@GET
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON  })
	public List<Employee> getAllEmployees() {
		return DB.getAllEmployees();
	}

	@GET
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
	@Path("{id}")
	public Employee getEmployee(@PathParam("id") int id) {
		return DB.getEmployee(id);
	}

}