/*******************************************************************************
 * Author: Ahmed Kosba <akosba@cs.umd.edu>
 *******************************************************************************/
package com.jsnark.circuit.operations.primitive;

import com.jsnark.circuit.config.Config;
import com.jsnark.circuit.structure.Wire;
import com.jsnark.util.Util;

import java.math.BigInteger;

public class PackBasicOp extends BasicOp {

	public PackBasicOp(Wire[] inBits, Wire out, String... desc) {
		super(inBits, new Wire[] { out }, desc);
	}

	public String getOpcode(){
		return "pack";
	}
	
	@Override
	public void checkInputs(BigInteger[] assignment) {
		super.checkInputs(assignment);
		boolean check = true;
		for (int i = 0; i < inputs.length; i++) {
			check &= Util.isBinary(assignment[inputs[i].getWireId()]);
		}
		if (!check) {
			System.err.println("Error - Input(s) to Pack are not binary. "
					+ this);
			throw new RuntimeException("Error During Evaluation");

		}
	}

	@Override
	public void compute(BigInteger[] assignment) {
		BigInteger sum = BigInteger.ZERO;
		for (int i = 0; i < inputs.length; i++) {
			sum = sum.add(assignment[inputs[i].getWireId()]
					.multiply(new BigInteger("2").pow(i)));
		}
		assignment[outputs[0].getWireId()] = sum.mod(Config.FIELD_PRIME);
	}

	@Override
	public boolean equals(Object obj) {

		if (this == obj)
			return true;
		if (!(obj instanceof PackBasicOp)) {
			return false;
		}
		PackBasicOp op = (PackBasicOp) obj;
		if (op.inputs.length != inputs.length)
			return false;

		boolean check = true;
		for (int i = 0; i < inputs.length; i++) {
			check = check && inputs[i].equals(op.inputs[i]);
		}
		return check;
	}
	
	@Override
	public int getNumMulGates() {
		return 0;
	}


}
