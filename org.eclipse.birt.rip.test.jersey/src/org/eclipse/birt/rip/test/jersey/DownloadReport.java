package org.eclipse.birt.rip.test.jersey;

import java.io.IOException;
import java.io.InputStream;
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

public class DownloadReport {
	public static void main(final String[] args) throws IOException {
		final String uriString = args[0];
		final String fileIdString = args[1];
		final ClientConfig config = new ClientConfig();
		config.register(HttpAuthenticationFeature.basic("steve", "foobar"));
		final Client client = ClientBuilder.newClient(config);
		final URI uri = UriBuilder.fromUri(uriString).build();
		final WebTarget target = client.target(uri).path("birt").path("report")
				.path("download").path(fileIdString);
		final Builder builder = target.request().accept(
				MediaType.APPLICATION_OCTET_STREAM_TYPE);
		final Response response = builder.get();
		System.out.println(response);
		final Object entity = response.getEntity();
		if (entity instanceof InputStream) {
			final InputStream inputStream = (InputStream) entity;
			try {
				final byte[] buffer = new byte[0x1000];
				int bytesRead = inputStream.read(buffer);
				while (bytesRead >= 0) {
					System.out.write(buffer, 0, bytesRead);
					bytesRead = inputStream.read(buffer);
				}
			} finally {
				inputStream.close();
			}
		}
	}
}
