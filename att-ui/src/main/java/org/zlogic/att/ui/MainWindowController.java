package org.zlogic.att.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.converter.DefaultStringConverter;
import org.zlogic.att.data.PersistenceHelper;
import org.zlogic.att.data.Task;
import org.zlogic.att.ui.adapters.TaskAdapter;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the main window
 * User: Dmitry Zolotukhin <zlogic@gmail.com>
 * Date: 29.12.12
 * Time: 22:18
 */
public class MainWindowController implements Initializable {
	private final static Logger log = Logger.getLogger(MainWindowController.class.getName());
	private PersistenceHelper storageManager = new PersistenceHelper();

	private Runnable shutdownProcedure;

	private Stage customFieldEditorStage;

	@FXML
	private TaskEditorController taskEditorController;

	@FXML
	private TableView<TaskAdapter> taskList;

	@FXML
	private TableColumn<TaskAdapter, String> columnTaskName;

	@FXML
	private TableColumn<TaskAdapter, Boolean> columnTaskCompleted;

	@Override
	public void initialize(URL url, ResourceBundle resourceBundle) {
		taskList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		reloadTasks();
		//Cell editors
		columnTaskName.setCellFactory(new Callback<TableColumn<TaskAdapter, String>, TableCell<TaskAdapter, String>>() {
			@Override
			public TableCell<TaskAdapter, String> call(TableColumn<TaskAdapter, String> p) {
				TextFieldTableCell<TaskAdapter, String> cell = new TextFieldTableCell<>();
				cell.setConverter(new DefaultStringConverter());
				return cell;
			}
		});
		columnTaskCompleted.setCellFactory(new Callback<TableColumn<TaskAdapter, Boolean>, TableCell<TaskAdapter, Boolean>>() {
			@Override
			public TableCell<TaskAdapter, Boolean> call(TableColumn<TaskAdapter, Boolean> taskAdapterBooleanTableColumn) {
				return new CheckBoxTableCell<>();
			}
		});

		//Set column sizes
		//TODO: make sure this keeps working correctly
		//taskList.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		columnTaskName.prefWidthProperty().bind(taskList.widthProperty().multiply(9).divide(10));
		columnTaskCompleted.prefWidthProperty().bind(taskList.widthProperty().multiply(1).divide(10).subtract(15));

		//Load other windows
		loadWindowCustomFieldEditor();
	}

	public void setShutdownProcedure(Runnable shutdownProcedure) {
		this.shutdownProcedure = shutdownProcedure;
	}

	private void loadWindowCustomFieldEditor() {
		//Load FXML
		customFieldEditorStage = new Stage();
		customFieldEditorStage.initModality(Modality.NONE);
		Parent root = null;
		FXMLLoader loader = new FXMLLoader(getClass().getResource("CustomFieldEditor.fxml")); //NOI18N
		loader.setLocation(getClass().getResource("CustomFieldEditor.fxml")); //NOI18N
		try {
			root = (Parent) loader.load();
		} catch (IOException ex) {
			Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error loading FXML", ex);
		}
		//Initialize the scene properties
		if (root != null) {
			Scene scene = new Scene(root);
			customFieldEditorStage.setTitle("Custom field editor");
			customFieldEditorStage.setScene(scene);
			//((CustomFieldEditorController) loader.getController()).messageText.setText(message);
		}
		//Set the custom fields list reference
		taskEditorController.setCustomFields(((CustomFieldEditorController) loader.getController()).getCustomFields());
	}

	private void reloadTasks() {
		taskList.getItems().clear();
		for (Task task : storageManager.getAllTasks())
			taskList.getItems().add(new TaskAdapter(task));
		taskEditorController.setEditedTaskList(taskList.getSelectionModel().getSelectedItems());
	}

	/*
	Callbacks
	 */
	@FXML
	private void createNewTask() {
		TaskAdapter newTask = new TaskAdapter(storageManager.createTask());
		taskList.getItems().add(newTask);
		taskList.getSelectionModel().select(newTask);
	}

	@FXML
	private void showCustomFieldEditor() {
		customFieldEditorStage.showAndWait();
	}

	@FXML
	private void exit() {
		if (shutdownProcedure != null)
			shutdownProcedure.run();
	}
}