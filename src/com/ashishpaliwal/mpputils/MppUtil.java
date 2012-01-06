/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.ashishpaliwal.mpputils;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import net.sf.mpxj.ConstraintType;
import net.sf.mpxj.MPXJException;
import net.sf.mpxj.ProjectFile;
import net.sf.mpxj.Relation;
import net.sf.mpxj.ResourceAssignment;
import net.sf.mpxj.Task;
import net.sf.mpxj.reader.ProjectReader;
import net.sf.mpxj.reader.ProjectReaderUtility;

import com.google.gdata.client.Service.GDataRequest;
import com.google.gdata.client.calendar.CalendarService;
import com.google.gdata.data.DateTime;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.calendar.CalendarEventEntry;
import com.google.gdata.data.extensions.When;
import com.google.gdata.util.ServiceException;

public class MppUtil {

	private boolean exclude_historical=false;
	
	public MppUtil(boolean exclude_historical) {
		this.exclude_historical=exclude_historical;
	}
	
    /**
     * Converts an MPP Task into Google Calendar Entry
     *
     * @param task  Task to be converted to event entry
     * @param parentName The hierarchical name of the parent task.
     * @return  {@link CalendarEventEntry} representation of the {@link Task}
     */
    protected List<CalendarEventEntry> convertTaskToCalenderEntry(Task task,
    			String parentName) {
        //System.out.println("Task "+ parentName + ":" + task);
        
        LinkedList<CalendarEventEntry> resultList = new LinkedList<CalendarEventEntry>();
        List<Task> childTasks = task.getChildTasks();
        
        if(childTasks.size() > 0) {
        	// Only leaf tasks are added to the calendar.
        	// Tasks with children are skipped because the
        	// children will be added.
        	for(Task childTask : childTasks) {
        		List<CalendarEventEntry> subEntries = convertTaskToCalenderEntry(childTask,
        					parentName + ":" + childTask.getName());
        		resultList.addAll(subEntries);;
        	}
        }
        else { 
        	//A leaf task.
        	CalendarEventEntry eventEntry = new CalendarEventEntry();
        	if(task.getName() == null || task.getStart() == null) {
        		return resultList;
        	}
        	eventEntry.setTitle(new PlainTextConstruct(task.getName()));        
        	When date = new When();
        	
        	//Only create tasks in the future? 
        	if(exclude_historical && task.getFinish().before(new Date())) {
        		return resultList;
        	}
        	date.setStartTime(new DateTime(task.getStart()));
        	eventEntry.addTime(date);
        	date.setEndTime(new DateTime(task.getFinish()));
        	
        	//Build up a description string that contains
        	//the full name (including parents) of the task
        	//and resources and notes.
        	StringBuffer description=new StringBuffer();
        	
        	description.append(parentName);
        	description.append(":" +  task.getName());
        	String notes = task.getNotes();
        	StringBuffer resources = new StringBuffer("\nResources:");
        	boolean first=true;
        	for(ResourceAssignment resource : task.getResourceAssignments() ) {
        		if(resource.getResource() != null) {
        			if(!first)
        				resources.append(",");
        			resources.append(resource.getResource().getName() );
        			first=false;
        		}
        		
        	}
       
        	if(notes!=null) 
        		description.append("\n"+ notes);
        	if(resources!=null)
        		description.append(resources);
        	
        	ConstraintType constraintType = task.getConstraintType();
        	if(constraintType != null) {
        		description.append("\n");
        		description.append(constraintType.toString());
        		description.append(": ");
        		if(task.getConstraintDate() != null)
        			description.append(task.getConstraintDate().toString());
        		
        	}
        	List<Relation> preconditions=task.getPredecessors();
        	if(preconditions != null) {
        		for(Relation relation : preconditions) {
        			switch(relation.getType()) {
        				case FINISH_FINISH:
        					description.append("\nMust finish at the same time as:");
        					break;
        				case FINISH_START:
        					description.append("\nMust start after the finish of:");
        					break;
        				case START_FINISH:
        					description.append("\nMust start before the finish of:");
        					break;
        				case START_START:
        					description.append("\nMust finish at the same time as:");
        					break;
        				default:
        					description.append("unknown constraint with:");
        			}
        			description.append(relation.getTargetTask().getName());
        		}
        	}
        	
        	description.append("\nProject Plan Task #:" + Integer.toString(task.getID()));
        	eventEntry.setSummary(new PlainTextConstruct(parentName + ":" + task.getName()));   
        	eventEntry.setContent(new PlainTextConstruct(description.toString()));
        	resultList.add(eventEntry);
        }
        return resultList;
    }

    /**
     * Add the entry to the Calendar. This method does not use batch operation
     *
     * @param tasks Tasks to be added
     * @param calendarService Calendar service to use
     * @param url   Calendar URL
     */
    public void updateCalenderWithEntry(List<Task> tasks, CalendarService calendarService, URL url) {
    	for (Task task : tasks) {
    		if(task.getParentTask() != null) {
    			//Skip tasks with parents because we will get them when we descend
    			//into the child task of that parent.
    			continue;
    		}
    		List<CalendarEventEntry> entries = convertTaskToCalenderEntry(task,"");
    		try {
    			GDataRequest req = calendarService.createBatchRequest(url);    		
    			for(CalendarEventEntry entry: entries) {
    				try {
    					System.out.println("sending task:" + entry.getTitle().getPlainText());
    					calendarService.insert(url, entry);

    				} catch (IOException e) {
    					System.err.println("error on task:" + entry.getTitle().getPlainText());
    					e.printStackTrace();            		
    				} catch (ServiceException e) {
    					System.err.println("error on task:" + entry.getTitle().getPlainText());            		
    					e.printStackTrace();
    				}
    			}
    			req.end();
    		}
    		catch(IOException e) {
    			e.printStackTrace();
    		}
    		catch(ServiceException e) {
    			e.printStackTrace();
    		}
    	}
    }

    /**
     * Reads the MPP file and update the Google calender with the specified
     * tasks
     *
     * @param mppFile   The MPP file to be uploaded
     */
    public void updateCalenderWithMppTask(String mppFile, String userName, String password, String calendarUrl) {
        if(mppFile == null) {
            System.err.println("mpp file is null.");
            return;
        }

        try {
            CalendarService cService = new CalendarService("Test Calendar");
            cService.setUserCredentials(userName, password);
            URL url = new URL(calendarUrl);
            System.out.println("URL is : "+url);
            List<Task> tasks = getAllTasks(mppFile);
            updateCalenderWithEntry(tasks, cService, url);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Retrieves all the Tasks from an MPP file
     *
     * @param mppFile   File to be read
     * @return  All {@link Task} presents in the MPP file
     */
    private List<Task> getAllTasks(String mppFile) throws IllegalAccessException, InstantiationException, MPXJException {
        ProjectReader mppReader = ProjectReaderUtility.getProjectReader(mppFile);
        ProjectFile mppProjectFile = mppReader.read(mppFile);
        return mppProjectFile.getAllTasks();
    }
}