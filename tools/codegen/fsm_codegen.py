#!/usr/bin/env python3
"""FSM codegen — YAML → Java (+ C++ stubs) for all schemas/fsm/*.fsm.yaml.

Reads: schemas/fsm/*.fsm.yaml
Writes Java to: java/ems-fsm/src/main/generated/io/crossasset/ems/fsm/generated/
Writes C++ to:  cpp/fsm/generated/

Run:
    python3 tools/codegen/fsm_codegen.py [--dry-run] [--java-only] [--cpp-only]
"""

import argparse
import sys
import textwrap
from pathlib import Path

import yaml

import os

REPO = Path(__file__).parent.parent.parent
FSM_DIR = REPO / "schemas" / "fsm"
# Allow Gradle to override output directory via env (e.g. FSM_JAVA_OUT=build/generated/...)
_env_java_out = os.environ.get("FSM_JAVA_OUT")
JAVA_OUT = Path(_env_java_out) if _env_java_out else (REPO / "java" / "ems-fsm" / "src" / "main" / "generated")
CPP_OUT = REPO / "cpp" / "fsm" / "generated"
JAVA_PKG = "io.crossasset.ems.fsm.generated"
JAVA_PKG_PATH = JAVA_OUT / JAVA_PKG.replace(".", "/")

# ──────────────────────────────────────────────────────────────────────────────
# Type mapping
# ──────────────────────────────────────────────────────────────────────────────

YAML_TO_JAVA = {
    "u8": "int",
    "u16": "int",
    "u32": "long",
    "u64": "long",
    "i8": "int",
    "i16": "int",
    "i32": "int",
    "i64": "long",
    "string": "String",
    "bool": "boolean",
    "timestamp": "long",
    "uuid": "String",
    "figi": "String",
    "lei": "String",
    "currency": "String",
}

# Boxed equivalents for nullable fields
JAVA_BOXED = {
    "int": "Integer",
    "long": "Long",
    "boolean": "Boolean",
}


def java_type_for_field(finfo: dict) -> str:
    """Return the Java type, boxed if nullable."""
    base = YAML_TO_JAVA[finfo["type"]]
    if finfo.get("nullable") and base in JAVA_BOXED:
        return JAVA_BOXED[base]
    return base

YAML_TO_CPP = {
    "u8": "uint8_t",
    "u16": "uint16_t",
    "u32": "uint32_t",
    "u64": "uint64_t",
    "i8": "int8_t",
    "i16": "int16_t",
    "i32": "int32_t",
    "i64": "int64_t",
    "string": "std::string",
    "bool": "bool",
    "timestamp": "int64_t",
    "uuid": "std::string",
    "figi": "std::string",
    "lei": "std::string",
    "currency": "std::string",
}


def snake_to_camel(name: str) -> str:
    parts = name.split("_")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])


def fsm_class_prefix(name: str) -> str:
    """'OrderFsm' → 'Order'; 'MultiLegFsm' → 'MultiLeg'"""
    if name.endswith("Fsm"):
        return name[:-3]
    return name


# ──────────────────────────────────────────────────────────────────────────────
# Guard / expression DSL compiler → Java
# ──────────────────────────────────────────────────────────────────────────────

class Token:
    def __init__(self, kind: str, value: str):
        self.kind = kind   # AND, OR, LPAREN, RPAREN, EQ, NEQ, LT, GT, LEQ, GEQ,
                           # PLUS, MINUS, CONTEXT_FIELD, PAYLOAD_FIELD, INT, STRING
        self.value = value

    def __repr__(self) -> str:
        return f"Token({self.kind!r}, {self.value!r})"


def tokenize(expr: str) -> list[Token]:
    tokens: list[Token] = []
    i = 0
    while i < len(expr):
        if expr[i].isspace():
            i += 1
        elif expr[i:i+3] == "AND" and (i + 3 >= len(expr) or not expr[i+3].isalnum()):
            tokens.append(Token("AND", "AND"))
            i += 3
        elif expr[i:i+2] == "OR" and (i + 2 >= len(expr) or not expr[i+2].isalnum()):
            tokens.append(Token("OR", "OR"))
            i += 2
        elif expr[i:i+2] == "==":
            tokens.append(Token("EQ", "=="))
            i += 2
        elif expr[i:i+2] == "!=":
            tokens.append(Token("NEQ", "!="))
            i += 2
        elif expr[i:i+2] == "<=":
            tokens.append(Token("LEQ", "<="))
            i += 2
        elif expr[i:i+2] == ">=":
            tokens.append(Token("GEQ", ">="))
            i += 2
        elif expr[i] == "<":
            tokens.append(Token("LT", "<"))
            i += 1
        elif expr[i] == ">":
            tokens.append(Token("GT", ">"))
            i += 1
        elif expr[i] == "+":
            tokens.append(Token("PLUS", "+"))
            i += 1
        elif expr[i] == "-":
            tokens.append(Token("MINUS", "-"))
            i += 1
        elif expr[i] == "(":
            tokens.append(Token("LPAREN", "("))
            i += 1
        elif expr[i] == ")":
            tokens.append(Token("RPAREN", ")"))
            i += 1
        elif expr[i] == "'":
            j = i + 1
            while j < len(expr) and expr[j] != "'":
                j += 1
            tokens.append(Token("STRING", expr[i+1:j]))
            i = j + 1
        elif expr[i].isdigit():
            j = i
            while j < len(expr) and expr[j].isdigit():
                j += 1
            tokens.append(Token("INT", expr[i:j]))
            i = j
        elif expr[i:].startswith("context."):
            i += 8
            j = i
            while j < len(expr) and (expr[j].isalnum() or expr[j] == "_"):
                j += 1
            tokens.append(Token("CONTEXT_FIELD", expr[i:j]))
            i = j
        elif expr[i:].startswith("payload."):
            i += 8
            j = i
            while j < len(expr) and (expr[j].isalnum() or expr[j] == "_"):
                j += 1
            tokens.append(Token("PAYLOAD_FIELD", expr[i:j]))
            i = j
        elif expr[i:].startswith("null"):
            tokens.append(Token("NULL", "null"))
            i += 4
        else:
            raise ValueError(f"Unexpected character at position {i} in: {expr!r}")
    return tokens


class ExprParser:
    """Recursive descent parser for the FSM guard/update DSL.

    grammar:
        expr      ::= or_expr
        or_expr   ::= and_expr ('OR' and_expr)*
        and_expr  ::= cmp_expr ('AND' cmp_expr)*
        cmp_expr  ::= arith (('==' | '!=' | '<' | '>' | '<=' | '>=') arith)?
        arith     ::= term (('+' | '-') term)*
        term      ::= 'null' | INT | STRING | CONTEXT_FIELD | PAYLOAD_FIELD | '(' expr ')'
    """

    def __init__(self, tokens: list[Token]):
        self.tokens = tokens
        self.pos = 0

    def peek(self) -> Token | None:
        return self.tokens[self.pos] if self.pos < len(self.tokens) else None

    def consume(self, kind: str | None = None) -> Token:
        t = self.tokens[self.pos]
        if kind and t.kind != kind:
            raise ValueError(f"Expected {kind}, got {t}")
        self.pos += 1
        return t

    def parse_expr(self) -> str:
        return self.parse_or()

    def parse_or(self) -> str:
        left = self.parse_and()
        parts = [left]
        while self.peek() and self.peek().kind == "OR":
            self.consume("OR")
            parts.append(self.parse_and())
        if len(parts) == 1:
            return parts[0]
        return "(" + " || ".join(parts) + ")"

    def parse_and(self) -> str:
        left = self.parse_cmp()
        parts = [left]
        while self.peek() and self.peek().kind == "AND":
            self.consume("AND")
            parts.append(self.parse_cmp())
        if len(parts) == 1:
            return parts[0]
        return "(" + " && ".join(parts) + ")"

    def parse_cmp(self) -> str:
        left = self.parse_arith()
        t = self.peek()
        if t and t.kind in ("EQ", "NEQ", "LT", "GT", "LEQ", "GEQ"):
            self.consume()
            right = self.parse_arith()
            # For string equality, use .equals() with null-safe form
            if t.kind == "EQ":
                # Check if right side is a string literal
                if right.startswith('"'):
                    return f"{right}.equals({left})"
                elif left.startswith('"'):
                    return f"{left}.equals({right})"
                else:
                    return f"({left} == {right})"
            elif t.kind == "NEQ":
                if right.startswith('"'):
                    return f"!{right}.equals({left})"
                elif left.startswith('"'):
                    return f"!{left}.equals({right})"
                else:
                    return f"({left} != {right})"
            else:
                op_map = {"LT": "<", "GT": ">", "LEQ": "<=", "GEQ": ">="}
                return f"({left} {op_map[t.kind]} {right})"
        return left

    def parse_arith(self) -> str:
        result = self.parse_term()
        while self.peek() and self.peek().kind in ("PLUS", "MINUS"):
            op = self.consume()
            rhs = self.parse_term()
            result = f"({result} {op.value} {rhs})"
        return result

    def parse_term(self) -> str:
        t = self.peek()
        if t is None:
            raise ValueError("Unexpected end of expression")
        if t.kind == "LPAREN":
            self.consume("LPAREN")
            inner = self.parse_expr()
            self.consume("RPAREN")
            return f"({inner})"
        elif t.kind == "NULL":
            self.consume()
            return "null"
        elif t.kind == "INT":
            self.consume()
            return t.value
        elif t.kind == "STRING":
            self.consume()
            return f'"{t.value}"'
        elif t.kind == "CONTEXT_FIELD":
            self.consume()
            return f"ctx.{snake_to_camel(t.value)}()"
        elif t.kind == "PAYLOAD_FIELD":
            self.consume()
            return f"payload.{snake_to_camel(t.value)}()"
        else:
            raise ValueError(f"Unexpected token {t!r}")


def compile_guard(expr: str) -> str:
    """Compile a guard expression string to Java boolean expression."""
    if not expr or expr.strip() == "":
        return "true"
    tokens = tokenize(expr.strip())
    parser = ExprParser(tokens)
    result = parser.parse_expr()
    if parser.pos != len(parser.tokens):
        raise ValueError(f"Unconsumed tokens in guard {expr!r}: {parser.tokens[parser.pos:]}")
    return result


def compile_update_expr(yaml_expr, field_type: str) -> str:
    """Compile a context update expression to a Java expression."""
    # YAML null → Python None → Java null
    if yaml_expr is None:
        return "null"
    # Handle Python bool (YAML true/false parsed as Python bool)
    if isinstance(yaml_expr, bool):
        return "true" if yaml_expr else "false"
    # Handle Python int literals (YAML integers)
    if isinstance(yaml_expr, int):
        if field_type == "long":
            return f"{yaml_expr}L"
        return str(yaml_expr)
    expr = str(yaml_expr).strip()
    if expr == "null":
        return "null"
    if expr.lower() == "true":
        return "true"
    if expr.lower() == "false":
        return "false"
    # For string types with simple literal values like "0", "1", "5"
    # that don't contain context/payload references
    if field_type == "String" and not expr.startswith("context.") and not expr.startswith("payload."):
        # It's a string literal value
        return f'"{expr}"'
    tokens = tokenize(expr)
    parser = ExprParser(tokens)
    result = parser.parse_arith()
    if parser.pos != len(parser.tokens):
        raise ValueError(f"Unconsumed tokens in update expr {yaml_expr!r}")
    # Cast to long if needed for u64/i64/u32 etc.
    if field_type in ("long", "Long") and result.isdigit():
        return result + "L"
    return result


# ──────────────────────────────────────────────────────────────────────────────
# Java codegen
# ──────────────────────────────────────────────────────────────────────────────

JAVA_HEADER = """// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/{source}.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py
"""

JAVA_COMMON_HEADER = """// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: tools/codegen/fsm_codegen.py
// Re-run: python3 tools/codegen/fsm_codegen.py
"""


def gen_state_enum(fsm: dict, prefix: str) -> str:
    states = fsm["states"]
    lines = [
        JAVA_HEADER.format(source=fsm["name"].replace("Fsm", "").lower()),
        f"package {JAVA_PKG};",
        "",
        "/** FSM states for {@link " + prefix + "FsmRunner}. */",
        f"public enum {prefix}FsmState {{",
    ]
    for i, s in enumerate(states):
        comment_parts = []
        if s.get("initial"):
            comment_parts.append("initial")
        if s.get("terminal"):
            comment_parts.append("terminal")
        if s.get("fix_ord_status"):
            comment_parts.append(f"OrdStatus={s['fix_ord_status']}")
        if s.get("fix_exec_type"):
            comment_parts.append(f"ExecType={s['fix_exec_type']}")
        sep = "," if i < len(states) - 1 else ";"
        if comment_parts:
            lines.append(f"  /** {', '.join(comment_parts)}. */")
        lines.append(f"  {s['name']}{sep}")
    lines += [
        "",
        "  public boolean isTerminal() {",
        "    return switch (this) {",
    ]
    terminal = [s["name"] for s in states if s.get("terminal")]
    if terminal:
        lines.append("      case " + ", ".join(terminal) + " -> true;")
    lines += [
        "      default -> false;",
        "    };",
        "  }",
        "",
        "  public boolean isInitial() {",
        "    return switch (this) {",
    ]
    initial = [s["name"] for s in states if s.get("initial")]
    if initial:
        lines.append("      case " + ", ".join(initial) + " -> true;")
    lines += [
        "      default -> false;",
        "    };",
        "  }",
        "}",
        "",
    ]
    return "\n".join(lines)


def gen_event_enum(fsm: dict, prefix: str) -> str:
    events = fsm["events"]
    lines = [
        JAVA_HEADER.format(source=fsm["name"].replace("Fsm", "").lower()),
        f"package {JAVA_PKG};",
        "",
        "/** FSM events for {@link " + prefix + "FsmRunner}. */",
        f"public enum {prefix}FsmEvent {{",
    ]
    for i, e in enumerate(events):
        sep = "," if i < len(events) - 1 else ";"
        desc = e.get("description", e["name"])
        lines.append(f"  /** {desc} */")
        lines.append(f"  {e['name']}{sep}")
    lines += [
        "}",
        "",
    ]
    return "\n".join(lines)


def java_field_type(field_info: dict) -> str:
    base = YAML_TO_JAVA[field_info["type"]]
    if field_info.get("nullable") and base not in ("String",):
        return f"@Nullable {base}"
    return base


def gen_context_record(fsm: dict, prefix: str) -> str:
    ctx = fsm["context_schema"]
    fields = list(ctx.items())

    lines = [
        JAVA_HEADER.format(source=fsm["name"].replace("Fsm", "").lower()),
        f"package {JAVA_PKG};",
        "",
        "import org.jspecify.annotations.Nullable;",
        "",
        "/** Mutable context carried by each {@link " + prefix + "FsmRunner} instance. */",
        "public final class " + prefix + "FsmContext {",
        "",
    ]

    # Fields
    for fname, finfo in fields:
        jtype = java_type_for_field(finfo)
        nullable = finfo.get("nullable", False)
        camel = snake_to_camel(fname)
        if nullable:
            lines.append(f"  private @Nullable {jtype} {camel};")
        else:
            lines.append(f"  private {jtype} {camel};")

    # Constructor (all fields)
    param_list = []
    for fname, finfo in fields:
        jtype = java_type_for_field(finfo)
        camel = snake_to_camel(fname)
        nullable = finfo.get("nullable", False)
        if nullable:
            param_list.append(f"@Nullable {jtype} {camel}")
        else:
            param_list.append(f"{jtype} {camel}")

    lines += [
        "",
        "  public " + prefix + "FsmContext(",
    ]
    for i, p in enumerate(param_list):
        sep = "," if i < len(param_list) - 1 else ""
        lines.append(f"      {p}{sep}")
    lines.append("  ) {")
    for fname, _ in fields:
        camel = snake_to_camel(fname)
        lines.append(f"    this.{camel} = {camel};")
    lines.append("  }")
    lines.append("")

    # Getters
    for fname, finfo in fields:
        jtype = java_type_for_field(finfo)
        camel = snake_to_camel(fname)
        nullable = finfo.get("nullable", False)
        if nullable:
            lines.append(f"  public @Nullable {jtype} {camel}() {{ return {camel}; }}")
        else:
            lines.append(f"  public {jtype} {camel}() {{ return {camel}; }}")

    lines.append("")

    # Copy method: returns a new context with one field changed per setter-style
    lines.append("  /** Return a copy with the given field updated. */")
    lines.append(f"  public {prefix}FsmContext with(")
    all_params = ", ".join(
        f"{'@Nullable ' if finfo.get('nullable') else ''}{java_type_for_field(finfo)} {snake_to_camel(fname)}"
        for fname, finfo in fields
    )
    lines.append(f"      {all_params}")
    lines.append("  ) {")
    ctor_args = ", ".join(snake_to_camel(fname) for fname, _ in fields)
    lines.append(f"    return new {prefix}FsmContext({ctor_args});")
    lines.append("  }")

    lines += ["}", ""]
    return "\n".join(lines)


def gen_payload_records(fsm: dict, prefix: str) -> str:
    lines = [
        JAVA_HEADER.format(source=fsm["name"].replace("Fsm", "").lower()),
        f"package {JAVA_PKG};",
        "",
        "import org.jspecify.annotations.Nullable;",
        "",
        "/** Payload record types for events that carry additional data. */",
        f"public final class {prefix}FsmPayloads {{",
        "",
        f"  private {prefix}FsmPayloads() {{}}",
        "",
    ]

    for event in fsm["events"]:
        schema = event.get("payload_schema")
        if not schema:
            continue
        ename = event["name"]
        items = list(schema.items())
        # record header
        lines.append(f"  /** Payload for {ename}. */")
        lines.append(f"  public record {ename}Payload(")
        for i, (fname, finfo) in enumerate(items):
            jtype = java_type_for_field(finfo)
            camel = snake_to_camel(fname)
            nullable = finfo.get("nullable", False)
            sep = "," if i < len(items) - 1 else ""
            if nullable:
                lines.append(f"    @Nullable {jtype} {camel}{sep}")
            else:
                lines.append(f"    {jtype} {camel}{sep}")
        lines.append("  ) {}")
        lines.append("")

    lines += ["}", ""]
    return "\n".join(lines)


def gen_effects_class(fsm: dict, prefix: str) -> str:
    lines = [
        JAVA_HEADER.format(source=fsm["name"].replace("Fsm", "").lower()),
        f"package {JAVA_PKG};",
        "",
        "import java.util.Map;",
        "",
        f"/** Sealed effect descriptors for {prefix}FsmRunner transitions. */",
        f"public sealed interface {prefix}FsmEffect {{",
        "",
        "  /** Cascade an event to another FSM instance. */",
        "  record EmitEvent(String targetFsm, String event) implements " + prefix + "FsmEffect {}",
        "",
        "  /** Emit an outbound FIX message. */",
        "  record PublishFixMessage(Map<String, String> args) implements " + prefix + "FsmEffect {}",
        "",
        "  /** Append an event-log audit record. */",
        "  record PublishEventLog(String event) implements " + prefix + "FsmEffect {}",
        "",
        "  /** Schedule a timer (arch-time-replay-server). */",
        "  record ScheduleTimer(Map<String, String> args) implements " + prefix + "FsmEffect {}",
        "",
        "  /** Cancel a pending timer. */",
        "  record CancelTimer(Map<String, String> args) implements " + prefix + "FsmEffect {}",
        "",
        "  /** Notify subscribers. */",
        "  record Notify(Map<String, String> args) implements " + prefix + "FsmEffect {}",
        "",
        "  /** Stamp identity chaining trace fields. */",
        "  record ChainIdentityStamp(Map<String, String> args) implements " + prefix + "FsmEffect {}",
        "}",
        "",
    ]
    return "\n".join(lines)


def effect_to_java(effect: dict, prefix: str) -> str:
    """Render one effect as a Java expression."""
    kind = effect["kind"]
    args = effect.get("args", {})

    def map_literal(d: dict) -> str:
        parts = ", ".join(f'"{k}", "{v}"' for k, v in d.items())
        return f"Map.of({parts})"

    if kind == "publish_event_log":
        return f'new {prefix}FsmEffect.PublishEventLog("{args.get("event", "")}")'
    elif kind == "emit_event":
        return f'new {prefix}FsmEffect.EmitEvent("{args.get("target_fsm", "")}", "{args.get("event", "")}")'
    elif kind == "publish_fix_message":
        return f"new {prefix}FsmEffect.PublishFixMessage({map_literal(args)})"
    elif kind == "schedule_timer":
        return f"new {prefix}FsmEffect.ScheduleTimer({map_literal(args)})"
    elif kind == "cancel_timer":
        return f"new {prefix}FsmEffect.CancelTimer({map_literal(args)})"
    elif kind == "notify":
        return f"new {prefix}FsmEffect.Notify({map_literal(args)})"
    elif kind == "chain_identity_stamp":
        return f"new {prefix}FsmEffect.ChainIdentityStamp({map_literal(args)})"
    elif kind == "update_context":
        # update_context is handled by the runner separately (context mutation)
        return None
    else:
        raise ValueError(f"Unknown effect kind: {kind!r}")


def extract_update_context(effects: list, ctx_schema: dict) -> dict:
    """Collect field→expression from all update_context effects in a transition."""
    updates = {}
    for e in effects:
        if e["kind"] == "update_context":
            for field, expr in e.get("args", {}).items():
                updates[field] = expr
    return updates


def gen_runner(fsm: dict, prefix: str) -> str:
    ctx_schema = fsm["context_schema"]
    state_names = {s["name"] for s in fsm["states"]}
    event_names = {e["name"] for e in fsm["events"]}

    # Build event → payload class name map
    event_payload = {}
    for event in fsm["events"]:
        if event.get("payload_schema"):
            event_payload[event["name"]] = f"{prefix}FsmPayloads.{event['name']}Payload"

    lines = [
        JAVA_HEADER.format(source=fsm["name"].replace("Fsm", "").lower()),
        f"package {JAVA_PKG};",
        "",
        "import java.util.List;",
        "import java.util.Map;",
        "",
        "/**",
        f" * Pure transition function for {prefix}Fsm.",
        " *",
        " * <p>Call {@link #transition} with the current state, event, context, and optional",
        " * payload. The method returns a {@link TransitionResult} with the new state,",
        " * updated context, and list of effect descriptors to dispatch.",
        " *",
        " * <p>This class is generated from schemas/fsm/{}.fsm.yaml — do not hand-edit.".format(
            fsm["name"].lower()
        ),
        " */",
        f"public final class {prefix}FsmRunner {{",
        "",
        f"  private {prefix}FsmRunner() {{}}",
        "",
        "  /**",
        "   * Execute one FSM transition.",
        "   *",
        "   * @param state   current state",
        "   * @param event   incoming event",
        "   * @param ctx     current context (will not be mutated; new context in result)",
        "   * @param rawPayload event payload (may be null for zero-payload events)",
        "   * @return transition result; {@link TransitionResult#isNoTransition()} if no matching row",
        "   */",
        f"  public static TransitionResult<{prefix}FsmState, {prefix}FsmContext, {prefix}FsmEffect>",
        "      transition(",
        f"          {prefix}FsmState state,",
        f"          {prefix}FsmEvent event,",
        f"          {prefix}FsmContext ctx,",
        "          Object rawPayload) {",
        "",
        "    return switch (state) {",
    ]

    # Group transitions by from-state
    from_map: dict[str, list[dict]] = {s["name"]: [] for s in fsm["states"]}
    for t in fsm["transitions"]:
        from_map[t["from"]].append(t)

    for state in fsm["states"]:
        sname = state["name"]
        transitions = from_map[sname]
        lines.append(f"      case {sname} -> switch (event) {{")

        # Group by event within this state
        event_map: dict[str, list[dict]] = {}
        for t in transitions:
            event_map.setdefault(t["event"], []).append(t)

        for ename, rows in event_map.items():
            lines.append(f"        case {ename} -> {{")
            # Cast payload to typed payload class if the event has a schema
            payload_class = event_payload.get(ename)
            if payload_class:
                lines.append(f"          var payload = ({payload_class}) rawPayload;")
            for row in rows:
                guard = row.get("guard")
                to_state = row["to"]
                effects = row.get("effects", [])
                updates = extract_update_context(effects, ctx_schema)

                # Build the non-update effects list
                effect_exprs = [effect_to_java(e, prefix) for e in effects if e["kind"] != "update_context"]
                effect_exprs = [x for x in effect_exprs if x is not None]

                # Build updated context expression
                if updates:
                    # Construct new context with updated fields
                    field_args = []
                    for fname, finfo in ctx_schema.items():
                        camel = snake_to_camel(fname)
                        if fname in updates:
                            jtype = java_type_for_field(finfo)
                            compiled = compile_update_expr(updates[fname], jtype)
                            field_args.append(compiled)
                        else:
                            field_args.append(f"ctx.{camel}()")
                    new_ctx_expr = f"ctx.with({', '.join(field_args)})"
                else:
                    new_ctx_expr = "ctx"

                if effect_exprs:
                    effects_str = "List.of(" + ", ".join(effect_exprs) + ")"
                else:
                    effects_str = "List.of()"

                indent = "          "
                if guard:
                    java_guard = compile_guard(guard)
                    lines.append(f"{indent}if ({java_guard}) {{")
                    lines.append(f"{indent}  yield TransitionResult.of(")
                    lines.append(f"{indent}    {prefix}FsmState.{to_state},")
                    lines.append(f"{indent}    {new_ctx_expr},")
                    lines.append(f"{indent}    {effects_str});")
                    lines.append(f"{indent}}}")
                else:
                    lines.append(f"{indent}yield TransitionResult.of(")
                    lines.append(f"{indent}  {prefix}FsmState.{to_state},")
                    lines.append(f"{indent}  {new_ctx_expr},")
                    lines.append(f"{indent}  {effects_str});")

            # After all guarded branches, if any had guards, emit fallthrough
            has_guards = any(r.get("guard") for r in rows)
            if has_guards:
                lines.append("          yield TransitionResult.noTransition(state);")

            lines.append("        }")  # end case EVENTNAME

        # Events with no transitions from this state → noTransition
        lines.append("        default -> TransitionResult.noTransition(state);")
        lines.append("      };")  # end case STATENAME switch(event)

    lines += [
        "    };",  # end switch(state)
        "  }",
        "}",
        "",
    ]
    return "\n".join(lines)


def _transition_result_src(pkg: str) -> str:
    return f"""\
// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: tools/codegen/fsm_codegen.py
package {pkg};

import java.util.List;

/**
 * Return value from the FSM transition function.
 *
 * @param <S> state enum type
 * @param <C> context type
 * @param <E> effect type
 */
public record TransitionResult<S, C, E>(
    S newState,
    C newContext,
    List<E> effects,
    boolean isNoTransition
) {{

  /** Normal transition result. */
  public static <S, C, E> TransitionResult<S, C, E> of(S newState, C newContext, List<E> effects) {{
    return new TransitionResult<>(newState, newContext, effects, false);
  }}

  /** No matching transition — state + context unchanged, no effects. */
  public static <S, C, E> TransitionResult<S, C, E> noTransition(S currentState) {{
    return new TransitionResult<>(currentState, null, List.of(), true);
  }}
}}
"""

NULLABLE_STUB_SRC = """\
// Minimal @Nullable stub for compilation without a full null-annotations library.
// Replace with org.jspecify or javax.annotation if available.
package org.jspecify.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE})
public @interface Nullable {}
"""


def gen_java_for_fsm(fsm: dict, dry_run: bool) -> list[str]:
    name = fsm["name"]
    prefix = fsm_class_prefix(name)
    written = []

    def write(filename: str, content: str):
        path = JAVA_PKG_PATH / filename
        if dry_run:
            print(f"DRY-RUN would write: {path}")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
            written.append(str(path))

    write(f"{prefix}FsmState.java", gen_state_enum(fsm, prefix))
    write(f"{prefix}FsmEvent.java", gen_event_enum(fsm, prefix))
    write(f"{prefix}FsmContext.java", gen_context_record(fsm, prefix))

    # Only generate payloads if any event has a schema
    if any(e.get("payload_schema") for e in fsm["events"]):
        write(f"{prefix}FsmPayloads.java", gen_payload_records(fsm, prefix))

    write(f"{prefix}FsmEffect.java", gen_effects_class(fsm, prefix))
    write(f"{prefix}FsmRunner.java", gen_runner(fsm, prefix))

    return written


# ──────────────────────────────────────────────────────────────────────────────
# C++ stub codegen (skeletal — compile verified in a follow-up)
# ──────────────────────────────────────────────────────────────────────────────

CPP_HEADER_TEMPLATE = """\
// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/{source}.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py
#pragma once
#include <cstdint>
#include <string>
#include <optional>
#include <variant>
#include <vector>

namespace crossasset::ems::fsm {{

// ── States ──────────────────────────────────────────────────────────────────
enum class {prefix}FsmState : uint8_t {{
{states}
}};

// ── Events ───────────────────────────────────────────────────────────────────
enum class {prefix}FsmEvent : uint16_t {{
{events}
}};

// ── Context ───────────────────────────────────────────────────────────────────
struct {prefix}FsmContext {{
{ctx_fields}
}};

// ── TransitionResult ──────────────────────────────────────────────────────────
struct {prefix}FsmTransitionResult {{
  {prefix}FsmState newState;
  {prefix}FsmContext newContext;
  // effects: TODO — will be replaced by generated effect variant
  bool isNoTransition;
}};

// ── Transition function stub ───────────────────────────────────────────────────
// TODO: implement from YAML via codegen in a follow-up commit.
{prefix}FsmTransitionResult transition(
    {prefix}FsmState state,
    {prefix}FsmEvent event,
    const {prefix}FsmContext& ctx) noexcept;

}} // namespace crossasset::ems::fsm
"""


def gen_cpp_header(fsm: dict) -> str:
    prefix = fsm_class_prefix(fsm["name"])
    states = "\n".join(f"  {s['name']}," for s in fsm["states"])
    events = "\n".join(f"  {e['name']}," for e in fsm["events"])
    ctx_fields = []
    for fname, finfo in fsm["context_schema"].items():
        ctype = YAML_TO_CPP[finfo["type"]]
        camel = snake_to_camel(fname)
        nullable = finfo.get("nullable", False)
        if nullable:
            ctx_fields.append(f"  std::optional<{ctype}> {camel}{{}};")
        else:
            ctx_fields.append(f"  {ctype} {camel}{{}};")
    return CPP_HEADER_TEMPLATE.format(
        source=fsm["name"].lower(),
        prefix=prefix,
        states=states,
        events=events,
        ctx_fields="\n".join(ctx_fields),
    )


# ──────────────────────────────────────────────────────────────────────────────
# Main
# ──────────────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="FSM YAML → Java/C++ codegen")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--java-only", action="store_true")
    parser.add_argument("--cpp-only", action="store_true")
    args = parser.parse_args()

    fsm_files = sorted(FSM_DIR.glob("*.fsm.yaml"))
    if not fsm_files:
        print("ERROR: no *.fsm.yaml files found", file=sys.stderr)
        sys.exit(1)

    total_written = []

    for fsm_path in fsm_files:
        with open(fsm_path) as f:
            fsm = yaml.safe_load(f)

        name = fsm.get("name", "?")
        print(f"Processing {fsm_path.name} ({name})")

        if not args.cpp_only:
            written = gen_java_for_fsm(fsm, args.dry_run)
            total_written.extend(written)
            if not args.dry_run:
                for p in written:
                    print(f"  wrote {p}")

        if not args.java_only:
            cpp_content = gen_cpp_header(fsm)
            prefix = fsm_class_prefix(name)
            cpp_path = CPP_OUT / f"{prefix.lower()}_fsm.hpp"
            if args.dry_run:
                print(f"  DRY-RUN would write: {cpp_path}")
            else:
                cpp_path.parent.mkdir(parents=True, exist_ok=True)
                cpp_path.write_text(cpp_content)
                print(f"  wrote {cpp_path}")

    # Write shared files (TransitionResult + @Nullable stub)
    if not args.cpp_only and not args.dry_run:
        tr_path = JAVA_PKG_PATH / "TransitionResult.java"
        tr_path.write_text(_transition_result_src(JAVA_PKG))
        print(f"  wrote {tr_path}")

        nullable_dir = JAVA_OUT / "org" / "jspecify" / "annotations"
        nullable_dir.mkdir(parents=True, exist_ok=True)
        (nullable_dir / "Nullable.java").write_text(NULLABLE_STUB_SRC)
        print(f"  wrote {nullable_dir / 'Nullable.java'}")

    print(f"\nDone. {len(total_written)} Java files written.")


if __name__ == "__main__":
    main()
