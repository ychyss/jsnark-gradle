/*******************************************************************************
 * Author: Ahmed Kosba <akosba@cs.umd.edu>
 *******************************************************************************/
package com.jsnark.circuit.operations;

import com.jsnark.circuit.structure.CircuitGenerator;
import com.jsnark.circuit.structure.Wire;

public abstract class Gadget {

	protected CircuitGenerator generator;
	protected String description;

	public Gadget(String...desc) {
		this.generator = CircuitGenerator.getActiveCircuitGenerator();
		if(desc.length > 0)
			this.description = desc[0];
		else
			this.description = "";
	}

	public abstract Wire[] getOutputWires();
	
	public String toString() {
		return  getClass().getSimpleName() + " " + description;
	}
	
	public String debugStr(String s) {
		return this + ":" + s;
	}
}
