package org.eclipse.birt.rip.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UploadReport {
	public static final void main(final String[] args) throws IOException {
		final String fileName = args[0];
		final File file = new File(fileName);
		final URL url = new URL(
				"http://localhost:8080/org.eclipse.birt.rip/birt/run/report/upload");
		final HttpURLConnection connection = (HttpURLConnection) url
				.openConnection();
		connection.setDoInput(true);
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type",
				"application/octet-stream");
		final OutputStream outputStream = connection.getOutputStream();
		try {
			final FileInputStream fis = new FileInputStream(file);
			try {
				final byte[] buffer = new byte[0x1000];
				int bytesRead = fis.read(buffer);
				while (bytesRead >= 0) {
					outputStream.write(buffer, 0, bytesRead);
					bytesRead = fis.read(buffer);
				}
			} finally {
				fis.close();
			}
		} finally {
			outputStream.close();
		}
		final String contentType = connection.getContentType();
		System.out.println(contentType);
		final InputStream inputStream = connection.getInputStream();
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
