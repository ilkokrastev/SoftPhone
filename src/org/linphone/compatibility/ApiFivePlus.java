package org.linphone.compatibility;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.linphone.Contact;
import org.linphone.mediastream.Version;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents.Insert;

/*
ApiFivePlus.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
/**
 * @author Sylvain Berfini
 */
@TargetApi(5)
public class ApiFivePlus {
	public static void overridePendingTransition(Activity activity, int idAnimIn, int idAnimOut) {
		activity.overridePendingTransition(idAnimIn, idAnimOut);
	}
	
	public static Intent prepareAddContactIntent(String displayName, String sipUri) {
		Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
		intent.putExtra(ContactsContract.Intents.Insert.NAME, displayName);
		
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			ArrayList<ContentValues> data = new ArrayList<ContentValues>();
			ContentValues sipAddressRow = new ContentValues();
			sipAddressRow.put(Contacts.Data.MIMETYPE, SipAddress.CONTENT_ITEM_TYPE);
			sipAddressRow.put(SipAddress.SIP_ADDRESS, sipUri);
			data.add(sipAddressRow);
			intent.putParcelableArrayListExtra(Insert.DATA, data);
		} else {
			// VoIP field not available, we store the address in the IM field
			intent.putExtra(ContactsContract.Intents.Insert.IM_HANDLE, sipUri);
			intent.putExtra(ContactsContract.Intents.Insert.IM_PROTOCOL, "sip");
		}
		  
		return intent;
	}
	
	public static List<String> extractContactNumbersAndAddresses(String id, ContentResolver cr) {
		List<String> list = new ArrayList<String>();

		Uri uri = Data.CONTENT_URI;
		String[] projection = {ContactsContract.CommonDataKinds.Im.DATA};

		// SIP addresses
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			String selection = new StringBuilder()
				.append(Data.CONTACT_ID)
				.append(" = ? AND ")
				.append(Data.MIMETYPE)
				.append(" = '")
				.append(ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
				.append("'")
				.toString();
			projection = new String[] {ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS};
			Cursor c = cr.query(uri, projection, selection, new String[]{id}, null);

			int nbId = c.getColumnIndex(ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS);
			while (c.moveToNext()) {
				list.add("sip:" + c.getString(nbId)); 
			}
			c.close();
		} else {
			String selection = new StringBuilder()
				.append(Data.CONTACT_ID).append(" =  ? AND ")
				.append(Data.MIMETYPE).append(" = '")
				.append(ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)
				.append("' AND lower(")
				.append(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL)
				.append(") = 'sip'")
				.toString();
			Cursor c = cr.query(uri, projection, selection, new String[]{id}, null);

			int nbId = c.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA);
			while (c.moveToNext()) {
				list.add("sip:" + c.getString(nbId)); 
			}
			c.close();
		}
		
		// Phone Numbers
		Cursor c = cr.query(Phone.CONTENT_URI, new String[] { Phone.NUMBER }, Phone.CONTACT_ID + " = " + id, null, null);
        while (c.moveToNext()) {
            String number = c.getString(c.getColumnIndex(Phone.NUMBER));
            list.add(number); 
        }
        c.close();

		return list;
	}
	
	@TargetApi(11)
	public static Cursor getContactsCursor(ContentResolver cr) {
		String req = Data.MIMETYPE + " = '" + CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                + "' AND " + CommonDataKinds.Phone.NUMBER + " IS NOT NULL";
		
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			req += " OR (" + Data.MIMETYPE + " = '" + CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE 
					+ "' AND " + ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS + " IS NOT NULL)";
        } else {
        	req += " OR (" + Contacts.Data.MIMETYPE + " = '" + CommonDataKinds.Im.CONTENT_ITEM_TYPE 
                    + " AND lower(" + CommonDataKinds.Im.CUSTOM_PROTOCOL + ") = 'sip')";
        }
		
		return getGeneralContactCursor(cr, req);
	}

	public static Cursor getSIPContactsCursor(ContentResolver cr) {
		String req = null;
		if (Version.sdkAboveOrEqual(Version.API09_GINGERBREAD_23)) {
			req = Data.MIMETYPE + " = '" + CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE 
					+ "' AND " + ContactsContract.CommonDataKinds.SipAddress.SIP_ADDRESS + " IS NOT NULL";
        } else {
        	req = Contacts.Data.MIMETYPE + " = '" + CommonDataKinds.Im.CONTENT_ITEM_TYPE 
                    + " AND lower(" + CommonDataKinds.Im.CUSTOM_PROTOCOL + ") = 'sip'";
        }
		
		return getGeneralContactCursor(cr, req);
	}
	
	private static Cursor getGeneralContactCursor(ContentResolver cr, String select) {
		
		String[] projection = new String[] { Data.CONTACT_ID, Data.DISPLAY_NAME };
		
		String query = Data.DISPLAY_NAME + " IS NOT NULL AND (" + select + ")";
		Cursor cursor = cr.query(Data.CONTENT_URI, projection, query, null, Data.DISPLAY_NAME + " ASC");
		
		MatrixCursor result = new MatrixCursor(cursor.getColumnNames());
		Set<String> groupBy = new HashSet<String>();
		while (cursor.moveToNext()) {
		    String name = cursor.getString(getCursorDisplayNameColumnIndex(cursor));
		    if (!groupBy.contains(name)) {
		    	groupBy.add(name);
		    	Object[] newRow = new Object[cursor.getColumnCount()];
		    	
		    	int contactID = cursor.getColumnIndex(Data.CONTACT_ID);
		    	int displayName = cursor.getColumnIndex(Data.DISPLAY_NAME);
		    	
		    	newRow[contactID] = cursor.getString(contactID);
		    	newRow[displayName] = cursor.getString(displayName);
		    	
		        result.addRow(newRow);
	    	}
	    }
		return result;
	}
	
	public static int getCursorDisplayNameColumnIndex(Cursor cursor) {
		return cursor.getColumnIndex(Data.DISPLAY_NAME);
	}

	public static Contact getContact(ContentResolver cr, Cursor cursor, int position) {
		try {
			cursor.moveToFirst();
			boolean success = cursor.move(position);
			if (!success)
				return null;
			
			String id = cursor.getString(cursor.getColumnIndex(Data.CONTACT_ID));
	    	String name = getContactDisplayName(cursor);
	        Uri photo = getContactPictureUri(id);
	        InputStream input = getContactPictureInputStream(cr, id);
	        
	        Contact contact;
	        if (input == null) {
	        	contact = new Contact(id, name);
	        }
	        else {
	        	contact = new Contact(id, name, photo, BitmapFactory.decodeStream(input));
	        }
	        
	        contact.setNumerosOrAddresses(Compatibility.extractContactNumbersAndAddresses(contact.getID(), cr));
	        
	        return contact;
		} catch (Exception e) {
			
		}
		return null;
	}
	
	public static InputStream getContactPictureInputStream(ContentResolver cr, String id) {
		Uri person = getContactPictureUri(id);
		return Contacts.openContactPhotoInputStream(cr, person);
	}
	
	private static String getContactDisplayName(Cursor cursor) {
		return cursor.getString(cursor.getColumnIndex(Data.DISPLAY_NAME));
	}
	
	private static Uri getContactPictureUri(String id) {
		return ContentUris.withAppendedId(Contacts.CONTENT_URI, Long.parseLong(id));
	}
}