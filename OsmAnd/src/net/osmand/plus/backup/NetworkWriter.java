package net.osmand.plus.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.IProgress;
import net.osmand.StreamWriter;
import net.osmand.plus.backup.BackupHelper.OnUploadFileListener;
import net.osmand.plus.settings.backend.backup.AbstractWriter;
import net.osmand.plus.settings.backend.backup.items.FileSettingsItem;
import net.osmand.plus.settings.backend.backup.items.SettingsItem;
import net.osmand.plus.settings.backend.backup.SettingsItemWriter;
import net.osmand.util.Algorithms;

import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class NetworkWriter implements AbstractWriter {

	private final BackupHelper backupHelper;
	private final OnUploadFileListener listener;

	public NetworkWriter(@NonNull BackupHelper backupHelper, @Nullable OnUploadFileListener listener) {
		this.backupHelper = backupHelper;
		this.listener = listener;
	}

	@Override
	public void write(@NonNull SettingsItem item) throws IOException {
		String fileName = item.getFileName();
		if (Algorithms.isEmpty(fileName)) {
			fileName = item.getDefaultFileName();
		}
		SettingsItemWriter<? extends SettingsItem> itemWriter = item.getWriter();
		if (itemWriter != null) {
			try {
				uploadItemInfo(item, fileName);
				uploadEntry(itemWriter, fileName);
			} catch (UserNotRegisteredException e) {
				throw new IOException(e.getMessage(), e);
			}
		} else {
			uploadItemInfo(item, fileName);
		}
	}

	private void uploadEntry(@NonNull SettingsItemWriter<? extends SettingsItem> itemWriter,
							 @NonNull String fileName) throws UserNotRegisteredException, IOException {
		if (itemWriter.getItem() instanceof FileSettingsItem) {
			FileSettingsItem fileSettingsItem = (FileSettingsItem) itemWriter.getItem();
			uploadDirWithFiles(itemWriter, fileSettingsItem.getFile());
		} else {
			uploadItemFile(itemWriter, fileName);
		}
	}

	private void uploadItemInfo(@NonNull SettingsItem item, String fileName) throws IOException {
		fileName = Algorithms.getFileWithoutDirs(fileName) + BackupHelper.INFO_EXT;
		try {
			String itemJson = item.toJson();
			InputStream inputStream = new ByteArrayInputStream(itemJson.getBytes("UTF-8"));
			StreamWriter streamWriter = new StreamWriter() {
				@Override
				public void write(OutputStream outputStream, IProgress progress) throws IOException {
					Algorithms.streamCopy(inputStream, outputStream, progress, 1024);
					outputStream.flush();
				}
			};
			backupHelper.uploadFileSync(fileName, item.getType().name(), streamWriter, listener);
		} catch (JSONException | UserNotRegisteredException e) {
			throw new IOException(e.getMessage(), e);
		}
	}

	private void uploadItemFile(@NonNull SettingsItemWriter<? extends SettingsItem> itemWriter,
								@NonNull String fileName) throws UserNotRegisteredException, IOException {
		StreamWriter streamWriter = new StreamWriter() {
			@Override
			public void write(OutputStream outputStream, IProgress progress) throws IOException {
				itemWriter.writeToStream(outputStream, progress);
			}
		};
		backupHelper.uploadFileSync(fileName, itemWriter.getItem().getType().name(), streamWriter, listener);
	}

	private void uploadDirWithFiles(@NonNull SettingsItemWriter<? extends SettingsItem> itemWriter,
									@NonNull File file) throws UserNotRegisteredException, IOException {
		FileSettingsItem fileSettingsItem = (FileSettingsItem) itemWriter.getItem();
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			if (files != null) {
				for (File subfolderFile : files) {
					uploadDirWithFiles(itemWriter, subfolderFile);
				}
			}
		} else {
			String subtypeFolder = fileSettingsItem.getSubtype().getSubtypeFolder();
			String fileName = Algorithms.isEmpty(subtypeFolder)
					? file.getName()
					: file.getPath().substring(file.getPath().indexOf(subtypeFolder) - 1);
			fileSettingsItem.setInputStream(new FileInputStream(file));
			uploadItemFile(itemWriter, fileName);
		}
	}
}
