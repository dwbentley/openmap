// **********************************************************************
// 
// <copyright>
// 
//  BBN Technologies, a Verizon Company
//  10 Moulton Street
//  Cambridge, MA 02138
//  (617) 873-8000
// 
//  Copyright (C) BBNT Solutions LLC. All rights reserved.
// 
// </copyright>
// **********************************************************************
// 
// $Source: /cvs/distapps/openmap/src/openmap/com/bbn/openmap/util/propertyEditor/NonEditablePropertyEditor.java,v $
// $RCSfile: NonEditablePropertyEditor.java,v $
// $Revision: 1.1.1.1 $
// $Date: 2003/02/14 21:35:49 $
// $Author: dietrick $
// 
// **********************************************************************


package com.bbn.openmap.util.propertyEditor;

import java.awt.Component;
import java.awt.event.*;
import javax.swing.*;
import java.beans.*;

/** 
 * A PropertyEditor that doesn't let you edit the property.
 */
public class NonEditablePropertyEditor extends PropertyEditorSupport {
    
    /** The Component returned by getCustomEditor(). */
    JLabel label;

    /** Create NonEditablePropertyEditor.  */
    public NonEditablePropertyEditor() {}

    //
    //	PropertyEditor interface
    //
    
    /** PropertyEditor interface.
     *  @return true 
     */
    public boolean supportsCustomEditor() {
	return true;
    }
    
    /**
     * Returns a JButton that will bring up a JFileChooser dialog.
     * @return JButton button
     */
    public Component getCustomEditor() {
	if (label == null) {
	    label = new JLabel();
	}
	return label;
    }
    
    /** Implement PropertyEditor interface. */
    public void setValue(Object someObj) {
	if(someObj instanceof String) {
	    label.setText((String)someObj);
	}
    }
    
    /** Implement PropertyEditor interface. */
    public String getAsText() {
	return label.getText();
    }
    
    //
    //	ActionListener interface
    //
    
    public void actionPerformed(ActionEvent e) {
    }
}