package com.ashishpaliwal.mpputils.examples;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import com.ashishpaliwal.mpputils.CalendarUtils;
import com.ashishpaliwal.mpputils.MppUtil;
import com.google.gdata.data.calendar.CalendarEntry;

/**
 * Converts a Microsoft Project file to a Google Calendar
 * 
 * usage:  MppToGoogleCalendar [--skip_completed] projectfile.mpp username
 */
public class MppToGoogleCalendar {

    public static void main(String[] args) throws Exception {

    	
    	boolean exclude_historical=false;
        
    	for(int idx=0; idx < args.length; idx++) {
    		if(args[idx].startsWith("--skip_completed")) {
    			exclude_historical=true;
    		}
    		else if(args[idx].startsWith("-")) {
    			System.err.println("MppToGoogleCalendar [--skip_completed] projfile.mpp myuser@gmail.com ");
    			return;
    		}
    	}
        if(args.length < 2) {
    		System.err.println("MppToGoogleCalendar [--skip_completed] projfile.mpp myuser@gmail.com ");
			return;
        }
    	CalendarUtils calendarUtils = new CalendarUtils();
        System.out.print("Enter password for " + args[args.length-1] + ":");
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String password = reader.readLine();
        List<CalendarEntry> calendars = CalendarUtils.getAllCalendars(args[args.length-1], password);

        int count = 1;
        System.out.println("Please select one of the Calendar");
        // display all Calendars
        for (Iterator<CalendarEntry> iterator = calendars.iterator(); iterator.hasNext(); ) {
            CalendarEntry next =  iterator.next();
            System.out.println(count++ + ". " + next.getTitle().getPlainText());
        }

        System.out.print("Enter your Choice: ");

        Scanner scanner = new Scanner(System.in);

        int choice = scanner.nextInt();
        System.out.println("You selected : "+choice);

        if(choice > calendars.size() || choice < 1) {
            System.err.println("Invalid Choice entered : "+ choice);
            return;
        }

        MppUtil utils = new MppUtil(exclude_historical);

        // Create URL
        String calendarUrlString = calendars.get(choice - 1).getLinks().get(0).getHref();
        System.out.println(calendarUrlString);

        utils.updateCalenderWithMppTask(args[args.length-2], args[args.length-1], password, calendarUrlString);
    }

}
