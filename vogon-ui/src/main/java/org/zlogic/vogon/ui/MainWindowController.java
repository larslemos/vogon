/*
 * Vogon personal finance/expense analyzer.
 * License TBD.
 * Author: Dmitry Zolotukhin <zlogic@gmail.com>
 */
package org.zlogic.vogon.ui;

import java.io.File;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javax.swing.JOptionPane;
import org.zlogic.vogon.data.CsvImporter;
import org.zlogic.vogon.data.DatabaseManager;
import org.zlogic.vogon.data.FileExporter;
import org.zlogic.vogon.data.FileImporter;
import org.zlogic.vogon.data.FinanceAccount;
import org.zlogic.vogon.data.FinanceData;
import org.zlogic.vogon.data.VogonExportException;
import org.zlogic.vogon.data.VogonImportLogicalException;
import org.zlogic.vogon.data.XmlExporter;
import org.zlogic.vogon.data.XmlImporter;

/**
 * Main entry window controller.
 *
 * @author Dmitry Zolotukhin
 */
public class MainWindowController implements Initializable {

	private java.util.ResourceBundle messages = java.util.ResourceBundle.getBundle("org/zlogic/vogon/ui/messages");
	/**
	 * Last opened directory
	 */
	private File lastDirectory;
	private FinanceData financeData;
	/**
	 * Easy access to preference storage
	 */
	protected java.util.prefs.Preferences preferenceStorage = java.util.prefs.Preferences.userNodeForPackage(Launcher.class);
	protected Thread backgroundThread;
	@FXML
	private VBox mainWindow;
	@FXML
	private HBox statusPane;
	@FXML
	private TransactionsController transactionsPaneController;
	@FXML
	private ProgressIndicator progressIndicator;
	@FXML
	private Label progressLabel;
	@FXML
	private MenuItem menuItemImport;
	@FXML
	private MenuItem menuItemExport;
	@FXML
	private MenuItem menuItemRecalculateBalance;
	@FXML
	private MenuItem menuItemCleanupDB;

	@FXML
	private void handleMenuExitAction(ActionEvent event) {
		completeTaskThread();
		DatabaseManager.getInstance().shutdown();
		Platform.exit();
	}

	@FXML
	private void handleMenuImportAction(ActionEvent event) {
		// Prepare file chooser dialog
		FileChooser fileChooser = new FileChooser();
		if (lastDirectory != null && lastDirectory.exists())
			fileChooser.setInitialDirectory(lastDirectory);
		fileChooser.setTitle(messages.getString("CHOOSE_FILES_TO_IMPORT"));
		//Prepare file chooser filter
		Map<String, String> extensionFilters = new TreeMap<>();
		extensionFilters.put(".xml", messages.getString("XML_FILES"));//NOI18N
		extensionFilters.put(".csv", messages.getString("CSV_FILES_(COMMA-SEPARATED)"));//NOI18N
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.getString("XML_FILES"), "*.xml"));//NOI18N
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.getString("CSV_FILES_(COMMA-SEPARATED)"), "*.csv"));//NOI18N

		//Show the dialog
		File selectedFile;
		if ((selectedFile = fileChooser.showOpenDialog(mainWindow.getScene().getWindow())) != null) {
			lastDirectory = selectedFile.isDirectory() ? selectedFile : selectedFile.getParentFile();
			preferenceStorage.put("lastDirectory", lastDirectory.toString()); //NOI18N

			//Choose the importer based on the file extension
			FileImporter importer = null;
			String extension = selectedFile.isFile() ? selectedFile.getName().substring(selectedFile.getName().lastIndexOf(".")) : null;
			extension = extensionFilters.get(extension);
			if (extension == null)
				importer = null;
			else if (extension.equals(messages.getString("CSV_FILES_(COMMA-SEPARATED)")))
				importer = new CsvImporter(selectedFile);
			else if (extension.equals(messages.getString("XML_FILES")))
				importer = new XmlImporter(selectedFile);

			//Prepare the background task
			Task<Void> task = new Task<Void>() {
				private FileImporter importer;

				public Task setImporter(FileImporter importer) {
					this.importer = importer;
					return this;
				}

				@Override
				protected Void call() throws Exception {
					try {
						updateMessage(messages.getString("TASK_IMPORTING_DATA"));
						updateProgress(-1, 1);
						beginBackgroundTask();
						if (importer == null)
							throw new VogonImportLogicalException(messages.getString("UNKNOWN_FILE_TYPE"));
						financeData.importData(importer);
						transactionsPaneController.setFinanceData(financeData);
						//TODO: fix this
						//accountsTableModel.setFinanceData(financeData);
					} catch (final org.zlogic.vogon.data.VogonImportLogicalException ex) {
						Logger.getLogger(MainWindowController.class.getName()).log(Level.SEVERE, null, ex);
						MessageDialog.showDialog(messages.getString("IMPORT_EXCEPTION_DIALOG_TITLE"), new MessageFormat(messages.getString("IMPORT_EXCEPTION_DIALOG_TEXT")).format(new Object[]{ex.getLocalizedMessage(), org.zlogic.vogon.data.Utils.getStackTrace(ex)}), true);
					} catch (Exception ex) {
						Logger.getLogger(MainWindowController.class.getName()).log(Level.SEVERE, null, ex);
						MessageDialog.showDialog(messages.getString("IMPORT_EXCEPTION_DIALOG_TITLE"), new MessageFormat(messages.getString("IMPORT_EXCEPTION_DIALOG_TEXT")).format(new Object[]{ex.getLocalizedMessage(), org.zlogic.vogon.data.Utils.getStackTrace(ex)}), true);
					} finally {
						endBackgroundTask();
						updateProgress(1, 1);
						updateMessage("");//NOI18N
					}
					return null;
				}
			}.setImporter(importer);
			//Bind the task's progress
			progressIndicator.progressProperty().bind(task.progressProperty());
			progressLabel.textProperty().bind(task.messageProperty());
			//Run the task
			startTaskThread(task);
		}
	}

	@FXML
	private void handleMenuExportAction(ActionEvent event) {
		// Prepare file chooser dialog
		FileChooser fileChooser = new FileChooser();
		if (lastDirectory != null && lastDirectory.exists())
			fileChooser.setInitialDirectory(lastDirectory);
		fileChooser.setTitle(messages.getString("CHOOSE_FILES_TO_EXPORT"));
		//Prepare file chooser filter
		Map<String, String> extensionFilters = new TreeMap<>();
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.getString("XML_FILES"), "*.xml"));//NOI18N

		//Show the dialog
		File selectedFile;
		if ((selectedFile = fileChooser.showSaveDialog(mainWindow.getScene().getWindow())) != null) {
			lastDirectory = selectedFile.isDirectory() ? selectedFile : selectedFile.getParentFile();
			preferenceStorage.put("lastDirectory", lastDirectory.toString()); //NOI18N

			//Set extension if necessary
			String extension = selectedFile.getName().contains(".") ? selectedFile.getName().substring(selectedFile.getName().lastIndexOf(".")) : null;
			if (extension == null || extension.isEmpty())
				selectedFile = new File(selectedFile.getParentFile(), selectedFile.getName() + ".xml");

			FileExporter exporter = new XmlExporter(selectedFile);
			//Prepare the background task
			Task<Void> task = new Task<Void>() {
				private FileExporter exporter;

				public Task setExporter(FileExporter exporter) {
					this.exporter = exporter;
					return this;
				}

				@Override
				protected Void call() throws Exception {
					try {
						updateMessage(messages.getString("TASK_EXPORTING_DATA"));
						updateProgress(-1, 1);
						beginBackgroundTask();

						financeData.exportData(exporter);
					} catch (final org.zlogic.vogon.data.VogonExportException ex) {
						Logger.getLogger(MainWindowController.class.getName()).log(Level.SEVERE, null, ex);
						MessageDialog.showDialog(messages.getString("EXPORT_EXCEPTION_DIALOG_TITLE"), new MessageFormat(messages.getString("EXPORT_EXCEPTION_DIALOG_TEXT")).format(new Object[]{ex.getLocalizedMessage(), org.zlogic.vogon.data.Utils.getStackTrace(ex)}), true);
					} catch (Exception ex) {
						Logger.getLogger(MainWindowController.class.getName()).log(Level.SEVERE, null, ex);
						MessageDialog.showDialog(messages.getString("EXPORT_EXCEPTION_DIALOG_TITLE"), new MessageFormat(messages.getString("EXPORT_EXCEPTION_DIALOG_TEXT")).format(new Object[]{ex.getLocalizedMessage(), org.zlogic.vogon.data.Utils.getStackTrace(ex)}), true);
					} finally {
						endBackgroundTask();
						updateProgress(1, 1);
						updateMessage("");//NOI18N
					}
					return null;
				}
			}.setExporter(exporter);
			//Bind the task's progress
			progressIndicator.progressProperty().bind(task.progressProperty());
			progressLabel.textProperty().bind(task.messageProperty());
			//Run the task
			startTaskThread(task);
		}
	}

	@FXML
	private void handleMenuCleanupDBAction(ActionEvent event) {
		//Prepare the task
		Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				updateMessage(messages.getString("TASK_CLEANING_UP_DB"));
				updateProgress(-1, 1);
				beginBackgroundTask();

				financeData.cleanup();

				endBackgroundTask();
				updateProgress(1, 1);
				updateMessage("");//NOI18N
				return null;
			}
		};
		//Bind the task's progress
		progressIndicator.progressProperty().bind(task.progressProperty());
		progressLabel.textProperty().bind(task.messageProperty());
		//Run the task
		startTaskThread(task);
	}

	@FXML
	private void handleMenuRecalculateBalanceAction(ActionEvent event) {
		//Prepare the task
		Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				updateMessage(messages.getString("TASK_RECALCULATING_BALANCE"));
				updateProgress(-1, 1);
				beginBackgroundTask();

				for (FinanceAccount account : financeData.getAccounts())
					financeData.refreshAccountBalance(account);
				//TODO: fix this
				//updateAccounts();

				endBackgroundTask();
				updateProgress(1, 1);
				updateMessage("");//NOI18N
				return null;
			}
		};
		//Bind the task's progress
		progressIndicator.progressProperty().bind(task.progressProperty());
		progressLabel.textProperty().bind(task.messageProperty());
		//Run the task
		startTaskThread(task);
	}

	private void beginBackgroundTask() {
		statusPane.setVisible(true);
		menuItemImport.setDisable(true);
		menuItemExport.setDisable(true);
		menuItemRecalculateBalance.setDisable(true);
		menuItemCleanupDB.setDisable(true);
	}

	private void endBackgroundTask() {
		statusPane.setVisible(false);
		menuItemImport.setDisable(false);
		menuItemExport.setDisable(false);
		menuItemRecalculateBalance.setDisable(false);
		menuItemCleanupDB.setDisable(false);
	}

	private void startTaskThread(Task task) {
		synchronized (this) {
			completeTaskThread();
			backgroundThread = new Thread(task);
			backgroundThread.setDaemon(true);
			backgroundThread.start();
		}
	}

	public void completeTaskThread() {
		synchronized (this) {
			if (backgroundThread != null) {
				try {
					backgroundThread.join();
					backgroundThread = null;
				} catch (InterruptedException ex) {
					Logger.getLogger(MainWindowController.class.getName()).log(Level.SEVERE, null, ex);
				}
			}
		}
	}

	@Override
	public void initialize(URL url, ResourceBundle rb) {
		//Restore settings
		lastDirectory = preferenceStorage.get("lastDirectory", null) == null ? null : new File(preferenceStorage.get("lastDirectory", null)); //NOI18N

		//Configure components
		statusPane.managedProperty().bind(statusPane.visibleProperty());
		statusPane.setVisible(false);
	}

	public FinanceData getFinanceData() {
		return financeData;
	}

	public void setFinanceData(FinanceData financeData) {
		this.financeData = financeData;
		transactionsPaneController.setFinanceData(financeData);
	}
}