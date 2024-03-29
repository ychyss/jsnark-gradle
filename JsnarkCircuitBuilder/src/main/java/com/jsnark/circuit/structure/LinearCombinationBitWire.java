/*******************************************************************************
 * Author: Ahmed Kosba <akosba@cs.umd.edu>
 *******************************************************************************/
package com.jsnark.circuit.structure;

public class LinearCombinationBitWire extends BitWire {

	public LinearCombinationBitWire(int wireId) {
		super(wireId);
	}
	
	public WireArray getBitWires() {
		return new WireArray(new Wire[]{this});
	}

}
