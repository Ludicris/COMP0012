package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Stack;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.Constants;

public class ConstantFolder
{
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	Stack<Number> constantStack = null; // store all constants

	HashMap<Integer, Number> variableMap = null; // store variable index and constant in that variable

	Stack<InstructionHandle> pushAndLoadInstructions = null; // store all push and load instructions

	List<InstructionHandle> loopPositions = null; // store the start and end of loops

	boolean deleteElse;
	boolean ifInLoop;

	public ConstantFolder(String classFilePath)
	{
		System.out.println("Class: " + classFilePath);
		try{
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	public void printConstants(ConstantPoolGen cpgen)
	{
		ConstantPool cp = cpgen.getConstantPool();
		int length = cp.getLength();

		for (int i = 1; i < length; i++) {
			Constant constant = cp.getConstant(i);

			// Check if the constant is of specific types: Integer, Long, Float, Double
			if (constant != null &&
					(constant.getTag() == Constants.CONSTANT_Integer || constant.getTag() == Constants.CONSTANT_Long || constant.getTag() == Constants.CONSTANT_Float || constant.getTag() == Constants.CONSTANT_Double)) {
				System.out.println("Constant #" + i + ": " + constant);
			}
		}
	}

	public void printInstructions(ClassGen cgen)
	{
		Method[] methods = cgen.getMethods();
		for (Method method : methods) {
			InstructionList il = new InstructionList(method.getCode().getCode());
			InstructionHandle[] handles = il.getInstructionHandles();

			System.out.println("Instructions for method: " + method.getName());

			for (InstructionHandle handle : handles) {
				System.out.println(handle.getInstruction());
			}
		}
	}

	public void optimize()
	{
		ClassGen cgen = new ClassGen(original);
		cgen.setMajor(50);
		cgen.setMinor(0);

		ConstantPoolGen cpgen = cgen.getConstantPool();

		System.out.println("---BEFORE OPTIMISATION---");
		printConstants(cpgen);
		printInstructions(cgen);

		// Implement your optimization here
		System.out.println("Start Optimisation");
		Method[] methods = cgen.getMethods();
		for (Method method : methods) {
			// initialize data structure for every method
			constantStack = new Stack<Number>();
			variableMap = new HashMap<Integer, Number>();
			pushAndLoadInstructions = new Stack<InstructionHandle>();
			loopPositions = new ArrayList<InstructionHandle>();
			ifInLoop = false;
			deleteElse = false;
			performConstantFoldingOptimisation(cgen, cpgen, method);
		}

		System.out.println("---AFTER OPTIMISATION---");
		printConstants(cpgen);
		printInstructions(cgen);

		this.optimized = cgen.getJavaClass();
	}

	public void performConstantFoldingOptimisation(ClassGen cgen, ConstantPoolGen cpgen, Method method)
	{
		// Get the method's instruction list of raw byte code
		InstructionList il = new InstructionList(method.getCode().getCode());

		MethodGen methodGen = new MethodGen(method.getAccessFlags(), method.getReturnType(), method.getArgumentTypes(), null, method.getName(), cgen.getClassName(), il, cpgen);

		InstructionHandle[] handles = il.getInstructionHandles();

		findLoopPositions(il);

		// iterate through all the instructions and perform optimisation according to their type
		for (InstructionHandle handle : handles) {
			Instruction instruction = handle.getInstruction();

			if (instruction instanceof ArithmeticInstruction) optimiseArithmeticInstruction(il, handle, cpgen);
			else if (instruction instanceof LoadInstruction) optimiseLoadInstruction(handle);
			else if (isConstantLoadOrPushInstruction(instruction)) optimiseConstantLoadOrPushInstruction(handle, cpgen);
			else if (instruction instanceof StoreInstruction) optimiseStoreInstruction(handle);
			else if (instruction instanceof ConversionInstruction) optimiseConversionInstruction(il, handle, cpgen);
			else if (instruction instanceof IfInstruction) optimiseIfComparisonInstruction(il, handle);
			else if (instruction instanceof LCMP) optimiseLongComparisonInstruction(il, handle, cpgen);
			else if (instruction instanceof GotoInstruction) optimiseGoToInstruction(il, handle);
			else ifInLoop = false;
		}

		methodGen.setMaxStack();
		methodGen.setMaxLocals();
		Method optimisedMethod = methodGen.getMethod();
		cgen.replaceMethod(method, optimisedMethod);
	}

	public void optimiseArithmeticInstruction(InstructionList il, InstructionHandle handle, ConstantPoolGen cpgen)
	{
		if (!ifInLoop) {
			System.out.println("---Optimising Arithmetic Instruction---");
			Instruction instruction = handle.getInstruction();
			// Calculate the result of arithmetic operation
			Number result = performArithmeticOperation(instruction);
			constantStack.push(result);
			deleteInstruction(il, pushAndLoadInstructions.pop());
			deleteInstruction(il, pushAndLoadInstructions.pop());
			Number value = constantStack.peek();
			// Load the result into the constant pool
			Instruction newLoadInstruction = generateNewLoadInstruction(value, cpgen);
			handle.setInstruction(newLoadInstruction);
			pushAndLoadInstructions.push(handle);
		}
	}

	public Number performArithmeticOperation(Instruction operator)
	{
		Number second = constantStack.pop();
		Number first = constantStack.pop();
		Number result;

		// Integer Operations
		if (operator instanceof IADD) result = first.intValue() + second.intValue();
		else if (operator instanceof ISUB) result = first.intValue() - second.intValue();
		else if (operator instanceof IMUL) result = first.intValue() * second.intValue();
		else if (operator instanceof IDIV) result = first.intValue() / second.intValue();

			// Long Operations
		else if (operator instanceof LADD) result = first.longValue() + second.longValue();
		else if (operator instanceof LSUB) result = first.longValue() - second.longValue();
		else if (operator instanceof LMUL) result = first.longValue() * second.longValue();
		else if (operator instanceof LDIV) result = first.longValue() / second.longValue();

			// Float Operations
		else if (operator instanceof FADD) result = first.floatValue() + second.floatValue();
		else if (operator instanceof FSUB) result = first.floatValue() - second.floatValue();
		else if (operator instanceof FMUL) result = first.floatValue() * second.floatValue();
		else if (operator instanceof FDIV) result = first.floatValue() / second.floatValue();

			// Double Operations
		else if (operator instanceof DADD) result = first.doubleValue() + second.doubleValue();
		else if (operator instanceof DSUB) result = first.doubleValue() - second.doubleValue();
		else if (operator instanceof DMUL) result = first.doubleValue() * second.doubleValue();
		else if (operator instanceof DDIV) result = first.doubleValue() / second.doubleValue();

		else throw new IllegalArgumentException("Invalid Operator for Arithmetic Expression");

		return result;
	}

	public void optimiseLoadInstruction(InstructionHandle handle)
	{
		Instruction instruction = handle.getInstruction();

		if (!(instruction instanceof ALOAD)) {
			System.out.println("---Optimising Load Instruction---");
			int key = ((LoadInstruction) instruction).getIndex();
			Number value = variableMap.get(key);
			constantStack.push(value);
			pushAndLoadInstructions.push(handle);
			ifInLoop = ifInLoop || checkLoopVariable(handle, key);
		}
	}

	public boolean isConstantLoadOrPushInstruction(Instruction instruction)
	{
		return (instruction instanceof ICONST || instruction instanceof LCONST ||
				instruction instanceof FCONST || instruction instanceof  DCONST ||
				instruction instanceof BIPUSH || instruction instanceof SIPUSH ||
				instruction instanceof LDC || instruction instanceof LDC2_W);
	}

	public void optimiseConstantLoadOrPushInstruction(InstructionHandle handle, ConstantPoolGen cpgen)
	{
		System.out.println("---Optimising Constant Load and Push Instruction---");
		Number value;
		Instruction instruction = handle.getInstruction();

		if (instruction instanceof ICONST) value = ((ICONST) instruction).getValue();
		else if (instruction instanceof LCONST) value = ((LCONST) instruction).getValue();
		else if (instruction instanceof FCONST) value = ((FCONST) instruction).getValue();
		else if (instruction instanceof DCONST) value = ((DCONST) instruction).getValue();
		else if (instruction instanceof BIPUSH) value = ((BIPUSH) instruction).getValue();
		else if (instruction instanceof SIPUSH) value = ((SIPUSH) instruction).getValue();
		else if (instruction instanceof LDC) value = (Number) ((LDC) instruction).getValue(cpgen);
		else if (instruction instanceof LDC2_W) value = ((LDC2_W) instruction).getValue(cpgen);
		else throw new IllegalArgumentException("Invalid instruction for load or push");

		constantStack.push(value);
		pushAndLoadInstructions.push(handle);
	}

	public void optimiseStoreInstruction(InstructionHandle handle)
	{
		System.out.println("---Optimising Store Instruction---");
		Instruction instruction = handle.getInstruction();
		int key = ((StoreInstruction) instruction).getIndex();
		Number value = constantStack.pop();
		variableMap.put(key, value);
		pushAndLoadInstructions.pop();
	}

	public void optimiseConversionInstruction (InstructionList il, InstructionHandle handle, ConstantPoolGen cpgen)
	{
		if (!ifInLoop){
			Instruction instruction = handle.getInstruction();
			boolean toIntegerInstruction = instruction instanceof L2I || instruction instanceof F2I || instruction instanceof D2I;
			boolean toLongInstruction = instruction instanceof I2L || instruction instanceof F2L || instruction instanceof D2L;
			boolean toFloatInstruction = instruction instanceof I2F || instruction instanceof L2F || instruction instanceof D2F;
			boolean toDoubleInstruction = instruction instanceof I2D || instruction instanceof L2D || instruction instanceof F2D;

			Number value = constantStack.pop();
			Number convertedValue = null;
			if (toIntegerInstruction) convertedValue = value.intValue();
			else if (toLongInstruction) convertedValue = value.longValue();
			else if (toFloatInstruction) convertedValue = value.floatValue();
			else if (toDoubleInstruction) convertedValue = value.doubleValue();

			constantStack.push(convertedValue);
			deleteInstruction(il, pushAndLoadInstructions.pop());
			Number finalValue = constantStack.peek();
			Instruction newLoadInstruction = generateNewLoadInstruction(finalValue, cpgen);
			handle.setInstruction(newLoadInstruction);
			pushAndLoadInstructions.push(handle);
		}
	}

	public void optimiseIfComparisonInstruction(InstructionList il, InstructionHandle handle)
	{
		if(!ifInLoop) {
			System.out.print("---Optimising If Comparison Instruction---");
			IfInstruction instruction = (IfInstruction) handle.getInstruction();

			if (getComparisonResult(il, instruction)) {
				deleteInstruction(il, handle);
				deleteElse = true;
			} else {
				// False outcome, no need for any of the instructions within the comparison
				InstructionHandle target = instruction.getTarget();
				deleteMultipleInstructions(il, handle, target.getPrev());
			}
		}
	}

	public void optimiseLongComparisonInstruction(InstructionList il, InstructionHandle handle, ConstantPoolGen cpgen)
	{
		if (!ifInLoop) {
			System.out.println("---Optimising Long Comparison Instruction---");
			long first = (Long) constantStack.pop();
			long second = (Long) constantStack.pop();

			int output;
			if (first > second) output = 1;
			else if (first < second) output = -1;
			else output = 0;

			deleteInstruction(il, pushAndLoadInstructions.pop());
			deleteInstruction(il, pushAndLoadInstructions.pop());
			constantStack.push(output);
			Instruction newLoadInstruction = generateNewLoadInstruction(output, cpgen);
			handle.setInstruction(newLoadInstruction);
			pushAndLoadInstructions.push(handle);
		}
	}

	public void optimiseGoToInstruction(InstructionList il, InstructionHandle handle)
	{
		if(deleteElse){
			System.out.println("---Optimising Goto Instruction---");
			GotoInstruction instruction = (GotoInstruction) handle.getInstruction();
			InstructionHandle target = instruction.getTarget();
			deleteMultipleInstructions(il, handle, target.getPrev());
			deleteElse = false;
		}
	}

	private static boolean zeroComparison(Instruction instruction)
	{
		return instruction instanceof IFLE || instruction instanceof IFLT || instruction instanceof IFGE ||
				instruction instanceof IFGT || instruction instanceof IFEQ || instruction instanceof IFNE;
	}

	private boolean getComparisonResult(InstructionList il, IfInstruction instruction)
	{
		if (zeroComparison(instruction)) {
			deleteInstruction(il, pushAndLoadInstructions.pop());
			Number num = constantStack.pop();
			if (instruction instanceof IFLE) return num.intValue() <= 0;
			else if (instruction instanceof  IFLT) return num.intValue() < 0;
			else if (instruction instanceof  IFEQ) return num.intValue() == 0;
			else if (instruction instanceof  IFNE) return num.intValue() != 0;
			else if (instruction instanceof  IFGE) return num.intValue() >= 0;
			else if (instruction instanceof  IFGT) return num.intValue() > 0;
			else throw new IllegalArgumentException("Invalid instruction for comparison with zero");
		} else {
			Number first = constantStack.pop();
			Number second = constantStack.pop();
			deleteInstruction(il, pushAndLoadInstructions.pop());
			deleteInstruction(il, pushAndLoadInstructions.pop());
			if (instruction instanceof IF_ICMPLE) return first.intValue() <= second.intValue();
			else if (instruction instanceof IF_ICMPLT) return first.intValue() < second.intValue();
			else if (instruction instanceof IF_ICMPEQ) return first.intValue() == second.intValue();
			else if (instruction instanceof IF_ICMPNE) return first.intValue() != second.intValue();
			else if (instruction instanceof IF_ICMPGE) return first.intValue() >= second.intValue();
			else if (instruction instanceof IF_ICMPGT) return first.intValue() > second.intValue();
			else throw new IllegalArgumentException("Invalid instruction for comparison of two numbers");
		}
	}

	private static Instruction generateNewLoadInstruction (Number value, ConstantPoolGen cpgen)
	{
		Instruction newLoadInstruction;
		int newConstantInPool;
		if (value instanceof Integer) {
			int numInt = (Integer) value;
			boolean isInRange = numInt >= 0 && numInt <= 5;
			if (isInRange) {
				newLoadInstruction = new ICONST(numInt);
			} else {
				newConstantInPool = cpgen.addInteger(numInt);
				newLoadInstruction = new LDC(newConstantInPool);
			}
		}
		else if (value instanceof Long) {
			long numLong = (Long) value;
			newConstantInPool = cpgen.addLong(numLong);
			newLoadInstruction = new LDC2_W(newConstantInPool);
		}
		else if (value instanceof Float) {
			float numFloat = (Float) value;
			newConstantInPool = cpgen.addFloat(numFloat);
			newLoadInstruction = new LDC(newConstantInPool);
		}
		else if (value instanceof Double){
			double numDouble = (Double) value;
			newConstantInPool = cpgen.addDouble(numDouble);
			newLoadInstruction = new LDC2_W(newConstantInPool);
		}
		else throw new IllegalArgumentException("Invalid value to load");
		return newLoadInstruction;
	}

	public void deleteInstruction(InstructionList il, InstructionHandle handle)
	{
		InstructionHandle next = handle.getNext();
		try{
			System.out.println("Deleting Instruction: " + handle.getInstruction());
			il.delete(handle);
		} catch (TargetLostException e) {
			// handle goto referencing that involves the instruction
			InstructionHandle[] targets = e.getTargets();
			for (InstructionHandle target : targets){
				InstructionTargeter[] its = target.getTargeters();
				for (InstructionTargeter it : its) {
					it.updateTarget(target, next);
				}
			}
		}
	}

	public void deleteMultipleInstructions(InstructionList il, InstructionHandle handle, InstructionHandle target)
	{
		try{
			il.delete(handle, target);
		} catch (TargetLostException e) {}
	}

	public void findLoopPositions(InstructionList instructionList)
	{
		InstructionHandle[] handles = instructionList.getInstructionHandles();
		for(InstructionHandle handle : handles){
			if(handle.getInstruction() instanceof GotoInstruction instruction){
				InstructionHandle target = instruction.getTarget();
				if(target.getPosition() < handle.getPosition()){
					loopPositions.add(target);
					loopPositions.add(handle);
				}
			}
		}
	}

	public InstructionHandle findStartOfLoopForInstruction(InstructionHandle handle)
	{
		int pos = handle.getPosition();
		for (int start = 0; start < loopPositions.size(); start += 2){ // increment by 2 to get next pairing
			InstructionHandle startInstruction = loopPositions.get(start);
			InstructionHandle endInstruction = loopPositions.get(start + 1);

			if (pos >= startInstruction.getPosition() && pos < endInstruction.getPosition()){
				return startInstruction;
			}
		}
		return null;
	}

	public boolean checkLoopVariable(InstructionHandle handle, int index)
	{
		InstructionHandle loopHandle = findStartOfLoopForInstruction(handle);
		Instruction instruction = loopHandle.getInstruction();
		while(loopHandle != null && !(instruction instanceof GotoInstruction)){ // goto means we are at end of loop
			if(instruction instanceof StoreInstruction && ((StoreInstruction) instruction).getIndex() == index) {
				return true; // check if the variable of position (index) is being changed
			} else if(instruction instanceof IINC && ((IINC) instruction).getIndex() == index){
				return true; // check if the variable of position (index) is being changed
			}
			loopHandle = loopHandle.getNext();
		}
		return false;
	}

	public void write(String optimisedFilePath)
	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
			this.optimized.dump(out);
		} catch (FileNotFoundException e) {
			// Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}
}
