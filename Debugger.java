import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.BreakpointRequest;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.*;
import java.util.Map;
import java.lang.String;


public class Debugger {
    private Class debugClass;
    private int[] breakPointLines;
    private JSONArray jsonResult;

    public Debugger(){
        JSONArray res = new JSONArray();
        this.setJsonResult(res);
    }

    public Class getDebugClass() {
        return debugClass;
    }

    public void setDebugClass(Class debugClass) {
        this.debugClass = debugClass;
    }

    public int[] getBreakPointLines() {
        return breakPointLines;
    }

    public void setBreakPointLines(int[] breakPointLines) {
        this.breakPointLines = breakPointLines;
    }

    public JSONArray getJsonResult() {return this.jsonResult; }

    public void setJsonResult(JSONArray jsonResult) {this.jsonResult = jsonResult; }


    //Create connection with VM
    public VirtualMachine connectAndLaunchVM() throws Exception {

        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager()
                .defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
        arguments.get("main").setValue(debugClass.getName());
        return launchingConnector.launch(arguments);
    }

    //Create classPrepareRequest on VM to enable debugging
    public void enableClassPrepareRequest(VirtualMachine vm) {
        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(debugClass.getName());
        classPrepareRequest.enable();
    }

    //Create breakPointRequests at each breakpoint
    public void setBreakPoints(VirtualMachine vm, ClassPrepareEvent event) throws AbsentInformationException {
        ClassType classType = (ClassType) event.referenceType();
        for(int lineNumber: breakPointLines) {
            try{
                Location location = classType.locationsOfLine(lineNumber).get(0);
                BreakpointRequest bpReq = vm.eventRequestManager().createBreakpointRequest(location);
                bpReq.enable();
            }catch(IndexOutOfBoundsException e){
                System.out.println(e);
            }

        }
    }

    //Write debug information into JSON Object
    public void writeVariablesToJSON(LocatableEvent event, int breakPoint) throws IncompatibleThreadStateException,
AbsentInformationException 
    {
        JSONObject line =  new JSONObject();
        JSONObject values = new JSONObject();

        int[] breakpointLines = getBreakPointLines();

        Method method = event.location().method();

        StackFrame stackFrame = event.thread().frame(0);
        if(stackFrame.location().toString().contains(debugClass.getName())) {
            Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(stackFrame.visibleVariables());
            for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
                if(!entry.getKey().name().equals("args")) {
                    values.put(entry.getKey().name(), entry.getValue());

                }
            }

            line.put("Line", stackFrame.location().toString());
            line.put("Function", method.name());
            line.put("Value", values);
            JSONArray result = getJsonResult();
            result.add(line);
            setJsonResult(result);

            System.out.println(line.toString());
        }
    }

    //Write debug information to Output.json
    public void fileWriter(JSONArray jResult) throws IOException{
        File file = new File("Output.json");
        //Gson gson = new GsonBuilder();

        if(file.isFile()){
            file.delete();
        }

        file.createNewFile();
        FileWriter writer = new FileWriter(file);

        if(jResult != null){
            writer.write(jResult.toJSONString());
        }

        writer.flush();
        writer.close();
    }

    public static void main(String[] args) throws Exception {

        System.out.println("Debugger Start. . . ");

        Debugger debuggerInstance = new Debugger();
        debuggerInstance.setDebugClass(Debuggee.class);

        //Line numbers of debuggee class to act as breakpoints.
        int[] breakPoints = {5, 7, 8, 9, 10, 12, 13, 15,16, 19, 25};

        debuggerInstance.setBreakPointLines(breakPoints);
        VirtualMachine vm = null;
        int breakPoint = 0;
        
        try {
            vm = debuggerInstance.connectAndLaunchVM();
            debuggerInstance.enableClassPrepareRequest(vm);
            EventSet eventSet = null;
            while ((eventSet = vm.eventQueue().remove()) != null) {
                for (Event event : eventSet) {
                    System.out.println(event);
                    if (event instanceof ClassPrepareEvent) {
                        debuggerInstance.setBreakPoints(vm, (ClassPrepareEvent)event);
                    }
                    if (event instanceof BreakpointEvent) {
                        debuggerInstance.writeVariablesToJSON((BreakpointEvent) event, breakPoint);
                        breakPoint++;
                    }
                    vm.resume();
                }
            }
        } catch (VMDisconnectedException e) {
            System.out.println("Virtual Machine is disconnected.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        JSONArray jResult = debuggerInstance.getJsonResult();
        debuggerInstance.fileWriter(jResult);
    }
}
