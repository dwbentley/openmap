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
// $Source: /cvs/distapps/openmap/src/openmap/com/bbn/openmap/graphicLoader/netmap/Symbol.java,v $
// $RCSfile: Symbol.java,v $
// $Revision: 1.1 $
// $Date: 2003/06/25 20:38:09 $
// $Author: dietrick $
// 
// **********************************************************************


package com.bbn.openmap.graphicLoader.netmap;


public class Symbol {   /* Symbol for sexp */
    String str;
    int type;

    Symbol(String s, int type) {
	this.str=s;
	this.type=type;
    }

    public String toString() {
	return this.str;
    }
}