package org.eclipse.birt.rip.test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GetParameters {

	public static void main(final String[] args) throws IOException {
		final String fileIdString = args[0];
		final URL url = new URL(
				"http://localhost:8080/org.eclipse.birt.rip/birt/run/report/parameters/"
						+ fileIdString);
		final HttpURLConnection connection = (HttpURLConnection) url
				.openConnection();
		connection.setDoInput(true);
		connection.setDoOutput(true);
		final String contentType = connection.getContentType();
		System.out.println(contentType);
		final InputStream inputStream = connection.getInputStream();
		final StringBuilder sb = new StringBuilder();
		try {
			final byte[] buffer = new byte[0x1000];
			int bytesRead = inputStream.read(buffer);
			while (bytesRead >= 0) {
				for (int i = 0; i < bytesRead; i++) {
					sb.append((char) buffer[i]);
				}
				bytesRead = inputStream.read(buffer);
			}
		} finally {
			inputStream.close();
		}
		System.out.println(sb);
	}

}
