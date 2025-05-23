/*	PAMGUARD - Passive Acoustic Monitoring GUARDianship.
 * To assist in the Detection Classification and Localisation 
 * of marine mammals (cetaceans).
 *  
 * Copyright (C) 2006 
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package UserInput;

import PamguardMVC.PamDataUnit;
import annotation.DataAnnotation;
import annotation.DataAnnotationType;

/**
 * 
 * @author David J McLaren
 *
 */

public class UserInputDataUnit extends PamDataUnit {

	private String userString;
	
	public UserInputDataUnit(long timeMilliseconds, String userString) {
		super(timeMilliseconds);
		this.userString = userString;
		this.setChannelBitmap(0b1111111111111111);
	}
	
	public String getUserString() {
		return userString;
	}

	public void setUserString(String userString) {
		this.userString = userString;
	}
	
	/**
	 * Return an html formatted summary string
	 * describing the detection which can be 
	 * used in tooltips anywhere in PAMGuard. 
	 * @return summary string 
	 */
	@Override
	public String getSummaryString() {
		String str = "<html>";
		str +=  getParentDataBlock().getDataName() + " - ";
		if (getDatabaseIndex() > 0) {
			str += "Database Index : " + getDatabaseIndex() + "<p>";
		}

		str += "<p>" + this.getUserString() + "<p>";
		
		int nAttotations = getNumDataAnnotations();
		for (int i = 0; i < nAttotations; i++) {
			DataAnnotation an = getDataAnnotation(i);
			DataAnnotationType ant = an.getDataAnnotationType();
			str += ant.getAnnotationName() + ": " + an.toString() + "<br>";
		}

		return str;
	}

}
