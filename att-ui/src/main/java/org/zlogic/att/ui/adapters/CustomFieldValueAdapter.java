package org.zlogic.att.ui.adapters;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.zlogic.att.data.CustomField;
import org.zlogic.att.data.PersistenceHelper;
import org.zlogic.att.data.TransactedChange;

import javax.persistence.EntityManager;

/**
 * Adapter to interface JPA with Java FX observable properties for CustomField classes with values.
 * User: Dmitry Zolotukhin <zlogic@gmail.com>
 * Date: 04.01.13
 * Time: 0:41
 */
public class CustomFieldValueAdapter {
	protected static PersistenceHelper persistenceHelper = new PersistenceHelper();
	private StringProperty value = new SimpleStringProperty();
	private CustomFieldAdapter customFieldAdapter;
	private TaskAdapter taskAdapter;

	public CustomFieldValueAdapter(CustomFieldAdapter customFieldAdapter) {
		this.customFieldAdapter = customFieldAdapter;

		updateFxProperties();

		//Change listeners
		this.value.addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> observableValue, String oldValue, String newValue) {
				if (oldValue != null && newValue != null && !oldValue.equals(newValue)) {
					//TODO: detect if the change was actually initiated by us
					persistenceHelper.performTransactedChange(new TransactedChange() {
						private String newValue;
						private CustomFieldAdapter customFieldAdapter;

						public TransactedChange setNewValue(CustomFieldAdapter customFieldAdapter, String newValue) {
							this.customFieldAdapter = customFieldAdapter;
							this.newValue = newValue;
							return this;
						}

						@Override
						public void performChange(EntityManager entityManager) {
							getTask().setTask(entityManager.merge(getTask().getTask()));
							CustomField foundCustomField = entityManager.merge(customFieldAdapter.getCustomField());
							getTask().getTask().setCustomField(foundCustomField, newValue);
						}
					}.setNewValue(getCustomField(), newValue));
					updateFxProperties();
				}
			}
		});
	}

	public void updateFxProperties() {
		if (taskAdapter != null && taskAdapter.getTask() != null) {
			String customFieldValue = taskAdapter.getTask().getCustomField(customFieldAdapter.getCustomField());
			value.setValue(customFieldValue == null ? "null" : customFieldValue);
		}
	}

	public CustomFieldAdapter getCustomField() {
		return customFieldAdapter;
	}

	public void setTask(TaskAdapter taskAdapter) {
		this.taskAdapter = taskAdapter;
		this.value.setValue(null);
		updateFxProperties();
	}

	public TaskAdapter getTask() {
		return taskAdapter;
	}

	public StringProperty nameProperty() {
		return customFieldAdapter.nameProperty();
	}

	public StringProperty valueProperty() {
		return value;
	}
}