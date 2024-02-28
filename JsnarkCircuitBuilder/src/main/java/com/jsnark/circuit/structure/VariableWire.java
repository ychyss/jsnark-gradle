/*******************************************************************************
 * Author: Ahmed Kosba <akosba@cs.umd.edu>
 *******************************************************************************/
package com.jsnark.circuit.structure;

public class VariableWire extends Wire {

	private WireArray bitWires;
	
	public VariableWire(int wireId) {
		super(wireId);
	}
	
	public VariableWire(WireArray bits) {
		super(bits);
	}


	WireArray getBitWires() {
		return bitWires;
	}

	void setBits(WireArray bitWires) {
		this.bitWires = bitWires;
	}

}
