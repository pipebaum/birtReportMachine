package org.eclipse.birt.rip.test.jersey;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriBuilder;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;

public class RunReport {

	public static void main(final String[] args) throws IOException {
		final String uriString = args[0];
		final String fileIdString = args[1];
		final String outputFormat = args[2];
		final String params = args[3];
		final ClientConfig config = new ClientConfig();
		config.register(HttpAuthenticationFeature.basic("steve", "foobar"));
		final Client client = ClientBuilder.newClient(config);
		final URI uri = UriBuilder.fromUri(uriString).build();
		final WebTarget target = client.target(uri).path("birt").path("report")
				.path("run").path(outputFormat).path(fileIdString);
		final Builder builder = target.request().accept(
				MediaType.APPLICATION_OCTET_STREAM_TYPE);
		final StreamingOutput streamingOutput = new StreamingOutput() {

			@Override
			public void write(final OutputStream output) throws IOException,
					WebApplicationException {
				final byte[] outputBytes = params.getBytes();
				output.write(outputBytes);
			}
		};
		final Response response = builder.post(Entity.entity(streamingOutput,
				MediaType.APPLICATION_JSON_TYPE));
		System.out.println(response.toString());
		final MediaType mediaType = response.getMediaType();
		System.out.println(mediaType);
		final int length = response.getLength();
		System.out.println(length);
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
