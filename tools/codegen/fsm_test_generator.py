import os
import yaml
from pathlib import Path

TYPE_MAP = {
    "u8": "Integer",
    "u16": "Integer",
    "u32": "Integer",
    "u64": "Long",
    "i8": "Integer",
    "i16": "Integer",
    "i32": "Integer",
    "i64": "Long",
    "string": "String",
    "bool": "Boolean",
    "timestamp": "Long",
    "uuid": "String",
    "figi": "String",
    "lei": "String",
    "currency": "String"
}

def get_default_value(type_str):
    if type_str == "string" or type_str in ["uuid", "figi", "lei", "currency"]:
        return "\"default\""
    if type_str == "u64" or type_str == "i64" or type_str == "timestamp":
        return "0L"
    if type_str == "bool":
        return "false"
    return "0"

def generate_fsm_tests(yaml_dir, output_dir):
    yaml_path = Path(yaml_dir)
    out_path = Path(output_dir)
    out_path.mkdir(parents=True, exist_ok=True)

    for fsm_file in yaml_path.glob("*.yaml"):
        if "schema" in fsm_file.name:
            continue

        with open(fsm_file, 'r') as f:
            fsm = yaml.safe_load(f)

        # FSM name is e.g., "OrderFsm"
        name = fsm['name'] 
        states = fsm['states']
        events = fsm['events']
        transitions = fsm['transitions']
        context_schema = fsm.get('context_schema', {})

        # Correct class names: {Name}FsmContext, {Name}FsmRunner, {Name}FsmPayloads
        # Since 'name' is already 'OrderFsm', we just use it.
        ctx_class = f"{name}Context"
        runner_class = f"{name}Runner"
        payloads_class = f"{name}Payloads"
        
        test_class_name = f"{name}GeneratedTest"
        package_name = "io.crossasset.ems.fsm.generated"
        
        code = []
        code.append(f"package {package_name};")
        code.append("")
        code.append(f"import static io.crossasset.ems.fsm.generated.{name}State.*;")
        code.append(f"import static io.crossasset.ems.fsm.generated.{name}Event.*;")
        code.append("import static org.junit.jupiter.api.Assertions.*;")
        code.append("")
        code.append("import io.crossasset.ems.fsm.generated.*;")
        code.append("import org.junit.jupiter.api.Test;")
        code.append("import java.util.*;")
        code.append("")
        code.append(f"class {test_class_name} {{")
        code.append("")

        # Minimal context generator helper
        code.append(f"  private static {ctx_class} minimalCtx() {{")
        ctx_args = []
        for field, props in context_schema.items():
            ctx_args.append(get_default_value(props['type']))
        
        code.append(f"    return new {ctx_class}({', '.join(ctx_args)});")
        code.append("  }")
        code.append("")

        # Helper to create dummy payloads
        for event in events:
            e_name = event['name']
            payload_schema = event.get('payload_schema', {})
            if payload_schema:
                payload_args = [get_default_value(p['type']) for p in payload_schema.values()]
                code.append(f"  private static Object create{e_name}Payload() {{")
                code.append(f"    return new {payloads_class}.{e_name}Payload({', '.join(payload_args)});")
                code.append("  }")
                code.append("")

        # Generate tests for every defined transition
        for i, trans in enumerate(transitions):
            from_state = trans['from']
            event = trans['event']
            to_state = trans['to']
            
            # Append index to avoid duplicate method names
            method_name = f"test_trans_{i}_{from_state}_{event}_to_{to_state}"
            
            code.append("  @Test")
            code.append(f"  void {method_name}() {{")
            code.append(f"    var ctx = minimalCtx();")
            
            event_def = next(e for e in events if e['name'] == event)
            payload_call = "null"
            if event_def.get('payload_schema'):
                payload_call = f"create{event}Payload()"
            
            code.append(f"    var result = {runner_class}.transition({from_state}, {event}, ctx, {payload_call});")
            code.append("    assertFalse(result.isNoTransition(), \"Expected transition from " + from_state + " on " + event + "\");")
            code.append(f"    assertEquals({to_state}, result.newState());")
            code.append("  }")
            code.append("")

        # Generate negative tests
        defined_trans = {f"{t['from']}->{t['event']}" for t in transitions}
        for s_idx, state in enumerate(states):
            s_name = state['name']
            for e_idx, event in enumerate(events):
                e_name = event['name']
                if f"{s_name}->{e_name}" not in defined_trans:
                    method_name = f"test_no_trans_{s_idx}_{e_idx}_{s_name}_{e_name}"
                    code.append("  @Test")
                    code.append(f"  void {method_name}() {{")
                    code.append(f"    var ctx = minimalCtx();")
                    
                    event_def = next(e for e in events if e['name'] == e_name)
                    payload_call = "null"
                    if event_def.get('payload_schema'):
                        payload_call = f"create{e_name}Payload()"

                    code.append(f"    var result = {runner_class}.transition({s_name}, {e_name}, ctx, {payload_call});")
                    code.append("    assertTrue(result.isNoTransition(), \"Expected no transition for " + s_name + " with " + e_name + "\");")
                    code.append("  }")
                    code.append("")

        code.append("}")

        with open(out_path / f"{test_class_name}.java", 'w') as f:
            f.write("\n".join(code))
        print(f"Generated {test_class_name}.java")

if __name__ == "__main__":
    import sys
    if len(sys.argv) < 3:
        print("Usage: python fsm_test_generator.py <yaml_dir> <output_dir>")
        sys.exit(1)
    generate_fsm_tests(sys.argv[1], sys.argv[2])
