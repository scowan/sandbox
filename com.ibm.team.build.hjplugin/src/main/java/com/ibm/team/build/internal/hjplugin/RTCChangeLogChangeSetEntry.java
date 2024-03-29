/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin;

import hudson.Util;
import hudson.model.User;
import hudson.scm.EditType;
import hudson.scm.ChangeLogSet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility=999)
public class RTCChangeLogChangeSetEntry extends RTCChangeLogSetEntry {

    private static final String FORMAT = "yyyy-MM-dd HH:mm:ss"; //$NON-NLS-1$

    @ExportedBean(defaultVisibility=999)
    public static class ChangeDesc implements Comparable<ChangeDesc> {
		/**
		 * Kind constant indicating the change has equivalent &quot;before&quot; and &quot;after&quot; states.
		 */
		private static final int NONE     = 0x00;

		/**
		 * Kind constant indicating the addition of a versionable item
		 */
		private static final int ADD      = 0x01;

		/**
		 * Kind constant indicating the modification of a versionable item
		 */
		private static final int MODIFY   = 0x02;

		/**
		 * Kind constant indicating that the item's name has changed in this
		 * transition. 
		 */
		private static final int RENAME   = 0x04;

		/**
		 * Kind constant indicating that the item's parent has changed in this
		 * transition.
		 */
		private static final int REPARENT = 0x08;

		/**
		 * Kind constant indicating the deletion of a versionable item
		 */
		private static final int DELETE   = 0x10;

		private String name;
		private String itemType;
		private String itemId;
		private String stateId;
		
		private int kind;
		
		public void setKind(int kind) {
			this.kind = kind;
		}
		
		public void setName(String versionableName) {
			this.name = versionableName;
		}
		
		public void setItemType(String versionableItemType) {
			this.itemType = versionableItemType;
		}

		public void setItemId(String versionableItemId) {
			this.itemId = versionableItemId;
		}

		public void setStateId(String versionableStateId) {
			this.stateId = versionableStateId;
		}

		@Exported
		public String getName() {
			// if a folder and not the root folder append a slash on the end of the name
			if (isFolderChange() && this.name.length() != 1) {
				return this.name + "/"; //$NON-NLS-1$
			}
			return this.name;
		}
		
		public String getItemId() {
			return itemId;
		}

		public String getStateId() {
			return stateId;
		}

		@Exported
		public boolean isFolderChange() {
			return itemType != null && itemType.endsWith("Folder"); //$NON-NLS-1$
		}
		
		@Exported
		public String getModificationKind() {
			String result;
			if (kind == NONE) {
				result = Messages.RTCChangeLogChangeSetEntry_no_changes();
			} else if (kind == ADD) {
				result = Messages.RTCChangeLogChangeSetEntry_added();
			} else if (kind == DELETE) {
				result = Messages.RTCChangeLogChangeSetEntry_deleted();
			} else if (((kind & MODIFY) == MODIFY) && ((kind & (RENAME + REPARENT)) != 0)) {
				result = Messages.RTCChangeLogChangeSetEntry_moved_modified();
			} else if ((kind & MODIFY) == MODIFY) {
				result = Messages.RTCChangeLogChangeSetEntry_modified();
			} else if ((kind & (RENAME + REPARENT)) != 0) {
				result = Messages.RTCChangeLogChangeSetEntry_moved();
			} else {
				result = Messages.RTCChangeLogChangeSetEntry_unknown();
			}
			return result;
		}
		
		@Exported
		public EditType getType() {
			EditType result;
			if (kind == ADD) {
				result = EditType.ADD;
			} else if (kind == DELETE) {
				result = EditType.DELETE;
			} else {
				result = EditType.EDIT;
			}
			return result;
		}

		public int compareTo(ChangeDesc o) {
			return name.compareTo(o.name);
		}
	}
	
    @ExportedBean(defaultVisibility=999)
    public static class WorkItemDesc {
    	private String number;
    	private String summary;
    	
    	public void setNumber(String number) {
    		this.number = number;
    	}
    	
    	public void setSummary(String summary) {
    		this.summary = summary;
    	}
    	
		@Exported
		public String getNumber() {
			return number;
		}
		
		@Exported
		public String getSummary() {
			return summary;
		}
		
		@Exported
		public String getMsg() {
			return Util.escape( number + ": " + summary); //$NON-NLS-1$
		}
    }
    
    private String action;
	private String changeSetItemId;
	private String componentItemId;
	private String componentName;
	private String owner;
	private String comment;
	private long additionalChanges;
	private Date changeSetModDate;
	private List<ChangeDesc> changes;
	private boolean changesSorted;
	private List<WorkItemDesc> workItems;
	private WorkItemDesc primaryWorkItem;

	public RTCChangeLogChangeSetEntry() {
		changes = new ArrayList<RTCChangeLogChangeSetEntry.ChangeDesc>();
		workItems = new ArrayList<RTCChangeLogChangeSetEntry.WorkItemDesc>(0);
	}

	public String getChangeSetItemId() {
		return changeSetItemId;
	}
	
	public String getComponentItemId() {
		return componentItemId;
	}
	
	public String getComponentName() {
		return componentName;
	}

	@Override
	@Exported
	public String getMsg() {
		if (workItems.isEmpty()) {
			return comment;
		} else {
			WorkItemDesc workItem = workItems.get(0);
			return workItem.getMsg() + " - " + comment; //$NON-NLS-1$
		}
	}
	
	@Exported
	public String getComment() {
		return Util.escape(comment);
	}

	@Exported
	public WorkItemDesc getWorkItem() {
		return primaryWorkItem;
	}
	
	@Exported
	public List<WorkItemDesc> getAdditionalWorkItems() {
		return workItems;
	}
	
	@Override
	@Exported
	public User getAuthor() {
		return User.get(owner);
	}

	@Override
	@Exported
	public Collection<String> getAffectedPaths() {
		List<String> result;
		List<ChangeDesc> affectedVersionables = getAffectedVersionables();
		if (affectedVersionables == null) {
			result = new ArrayList<String>(1);
		} else {
			result = new ArrayList<String>(affectedVersionables.size() + 1);
			for (ChangeDesc change : affectedVersionables) {
				result.add(change.name);
			}
		}
		if (additionalChanges > 0) {
			result.add(Messages.RTCChangeLogChangeSetEntry_changes(additionalChanges));
		}
		return result;
	}
	
	@Exported
	public List<ChangeDesc> getAffectedVersionables() {
		if (!changesSorted) {
			Collections.sort(changes);
			changesSorted = true;
		}
		return changes;
	}
	
	@Exported
	public boolean isTooManyChanges() {
		return additionalChanges > 0;
	}

	@Exported
	public String getTooManyChangesMsg() {
		return Messages.RTCChangeLogChangeSetEntry_too_many_changes(additionalChanges);
	}

	@Override
	@Exported
	public long getTimestamp() {
		return changeSetModDate.getTime();
	}

	@Exported
	public String getChangeSetModDate() {
		SimpleDateFormat formatter = new SimpleDateFormat(FORMAT);
		return formatter.format(changeSetModDate);
	}
	
	@Exported
	public String getOwner() {
		return owner;
	}
	
	public boolean isAccept() {
		return action.equalsIgnoreCase("Added"); //$NON-NLS-1$
	}

	public void setAction(String action) {
		this.action = action;
	}
	
	public void setAdditionalChanges(int additionalChanges) {
		this.additionalChanges = additionalChanges;
	}
	
	public void setDate(String dateStr) {
		this.changeSetModDate = new Date(Long.parseLong(dateStr));
	}
	
	public void setComment(String comment) {
		this.comment = Util.fixEmptyAndTrim(comment);
	}
	
	public void setOwner(String owner) {
		this.owner = owner;
	}
	
	public void setChangeSetItemId(String itemId) {
		this.changeSetItemId = itemId;
	}

	public void setComponentItemId(String itemId) {
		this.componentItemId = itemId;
	}
	
	public void setComponentName(String componentName) {
		this.componentName = componentName;
	}

	public void addChange(RTCChangeLogChangeSetEntry.ChangeDesc change) {
		this.changes.add(change);
	}
	
	public void addWorkItem(RTCChangeLogChangeSetEntry.WorkItemDesc workItem) {
		if (this.primaryWorkItem == null) {
			this.primaryWorkItem = workItem;
		} else {
			this.workItems.add(workItem);
		}
	}

	public String getWorkspaceItemId() {
		ChangeLogSet parent = getParent();
		if (parent instanceof RTCChangeLogSet) {
			return ((RTCChangeLogSet) parent).getWorkspaceItemId();
		}
		return null;
	}

}
