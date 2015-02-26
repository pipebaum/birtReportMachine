package org.eclipse.birt.rip.test.jersey;

import java.io.IOException;
import java.net.URI;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;

public class GetParameters {

	public static void main(final String[] args) throws IOException {
		final String uriString = args[0];
		final String fileIdString = args[1];
		final ClientConfig config = new ClientConfig();
		config.register(HttpAuthenticationFeature.basic("steve", "foobar"));
		final Client client = ClientBuilder.newClient(config);
		final URI uri = UriBuilder.fromUri(uriString).build();
		final WebTarget target = client.target(uri).path("birt").path("report")
				.path("parameters").path(fileIdString);
		final Builder builder = target.request().accept(
				MediaType.APPLICATION_JSON_TYPE);
		final Response response = builder.get();
		System.out.println(response);
		final Object entity = response.getEntity();
		System.out.println(entity);
		final MediaType mediaType = response.getMediaType();
		System.out.println(mediaType);
		final int length = response.getLength();
		System.out.println(length);
		final String string = response.readEntity(String.class);
		System.out.println(string);
	}
}
