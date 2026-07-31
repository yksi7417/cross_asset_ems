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
# C++ full codegen (inline transition implementation, header-only)
# ──────────────────────────────────────────────────────────────────────────────

CPP_HEADER_TEMPLATE = """\
// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/{source}.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py
#pragma once
#include <array>
#include <cstdint>
#include <string>
#include <optional>
#include <span>
#include <string_view>
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

// ── Names ────────────────────────────────────────────────────────────────────
//
// State and event names reach the output journal, which the conformance gate
// compares byte-for-byte across three languages — so these must match the Java
// enum constants character for character.

inline const char* name({prefix}FsmState state) noexcept {{
  switch (state) {{
{state_names}
  }}
  return "UNKNOWN";
}}

inline const char* name({prefix}FsmEvent event) noexcept {{
  switch (event) {{
{event_names}
  }}
  return "UNKNOWN";
}}

/// Parses a schema event name. nullopt for anything the schema does not define.
///
/// A journal can carry any string; an unrecognised one is data, not a defect,
/// so the caller decides what to do rather than being handed undefined behaviour.
inline std::optional<{prefix}FsmEvent> {prefix}FsmEventFromName(std::string_view name) {{
{event_from_name}
  return std::nullopt;
}}

// ── Context ───────────────────────────────────────────────────────────────────
struct {prefix}FsmContext {{
{ctx_fields}
}};

// ── Effects ──────────────────────────────────────────────────────────────────
//
// A side effect a transition asks for, as the schema declares it.
//
// STUDY: effects-as-static-data
//
// Every value comes from the YAML, so a transition's effects are compile-time
// data: the generator emits `static constexpr` arrays and `transition` returns a
// span over one of them. Nothing is allocated and nothing has to be kept alive.
//
// The honest caveat, which Rust does not have: this is a struct with a `kind`
// tag, not a sum type. An `EmitEvent` still *has* an `args` field and a
// `PublishFixMessage` still has a `targetFsm` — both empty, and nothing stops a
// caller reading them. Java models this as a sealed interface of records and
// Rust as an enum, where both are unrepresentable. `std::variant` could express
// it, at the cost of every read becoming a visit; the tag was chosen because the
// only consumer switches on the kind anyway.
//
// The kind and arg types are prefixed like everything else here for a concrete
// reason: a translation unit that drives two machines includes two of these
// headers, and an unprefixed `FsmEffectKind` in the shared namespace would be a
// redefinition. Rust has no such problem — each machine is its own module.
enum class {prefix}FsmEffectKind : uint8_t {{
  EmitEvent,
  PublishEventLog,
  PublishFixMessage,
  ScheduleTimer,
  CancelTimer,
  Notify,
  ChainIdentityStamp,
}};

/// One `key: value` pair from an effect's `args` map in the schema.
struct {prefix}FsmEffectArg {{
  std::string_view key{{}};
  std::string_view value{{}};
}};

struct {prefix}FsmEffect {{
  {prefix}FsmEffectKind kind{{}};
  /// EmitEvent only — the machine the event is for. Empty otherwise.
  std::string_view targetFsm{{}};
  /// EmitEvent and PublishEventLog — the event name. Empty otherwise.
  std::string_view event{{}};
  /// Everything else — the raw `args` map. Empty for the two above.
  std::span<const {prefix}FsmEffectArg> args{{}};
}};

// ── TransitionResult ──────────────────────────────────────────────────────────
struct {prefix}FsmTransitionResult {{
  {prefix}FsmState newState;
  {prefix}FsmContext newContext;
  /// What the schema asks the caller to do, in the order it declares them.
  ///
  /// A span over static storage, so copying the result copies a pointer and a
  /// length. Empty when no transition matched.
  std::span<const {prefix}FsmEffect> effects;
  bool isNoTransition;
}};

{payload_struct}
{transition_impl}
}} // namespace crossasset::ems::fsm
"""


class ExprParserCpp:
    """C++ expression compiler — same DSL grammar as ExprParser, C++ emission.

    Differences from the Java ExprParser:
    - context.field  → ctx.camelField  (struct member, not method call)
    - payload.field  → p->camelField
    - null           → std::nullopt
    - optional string EQ/NEQ get has_value() + dereference guards
    - optional field == null  → !ctx.field.has_value()
    """

    def __init__(self, tokens: list[Token], ctx_schema: dict):
        self.tokens = tokens
        self.pos = 0
        self.ctx_schema = ctx_schema

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
        return ("(" + " || ".join(parts) + ")") if len(parts) > 1 else parts[0]

    def parse_and(self) -> str:
        left = self.parse_cmp()
        parts = [left]
        while self.peek() and self.peek().kind == "AND":
            self.consume("AND")
            parts.append(self.parse_cmp())
        return ("(" + " && ".join(parts) + ")") if len(parts) > 1 else parts[0]

    def parse_cmp(self) -> str:
        first_left_tok = self.peek()
        left = self.parse_arith()
        t = self.peek()
        if t and t.kind in ("EQ", "NEQ", "LT", "GT", "LEQ", "GEQ"):
            op_tok = self.consume()
            first_right_tok = self.peek()
            right = self.parse_arith()
            op_map = {"LT": "<", "GT": ">", "LEQ": "<=", "GEQ": ">="}

            # context.nullable_field == null / != null
            if (first_left_tok and first_left_tok.kind == "CONTEXT_FIELD" and
                    first_right_tok and first_right_tok.kind == "NULL"):
                finfo = self.ctx_schema.get(first_left_tok.value, {})
                if finfo.get("nullable"):
                    if op_tok.kind == "EQ":
                        return f"(!{left}.has_value())"
                    else:
                        return f"({left}.has_value())"

            # context.nullable_string_field == 'literal' / != 'literal'
            if (op_tok.kind in ("EQ", "NEQ") and
                    first_left_tok and first_left_tok.kind == "CONTEXT_FIELD" and
                    first_right_tok and first_right_tok.kind == "STRING"):
                finfo = self.ctx_schema.get(first_left_tok.value, {})
                if finfo.get("nullable") and finfo.get("type") == "string":
                    val = first_right_tok.value
                    if op_tok.kind == "EQ":
                        return f'({left}.has_value() && *{left} == "{val}")'
                    else:
                        return f'(!{left}.has_value() || *{left} != "{val}")'

            if op_tok.kind == "EQ":
                return f"({left} == {right})"
            elif op_tok.kind == "NEQ":
                return f"({left} != {right})"
            else:
                return f"({left} {op_map[op_tok.kind]} {right})"
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
            return "std::nullopt"
        elif t.kind == "INT":
            self.consume()
            return t.value
        elif t.kind == "STRING":
            self.consume()
            return f'"{t.value}"'
        elif t.kind == "CONTEXT_FIELD":
            self.consume()
            return f"ctx.{snake_to_camel(t.value)}"
        elif t.kind == "PAYLOAD_FIELD":
            self.consume()
            return f"p->{snake_to_camel(t.value)}"
        else:
            raise ValueError(f"Unexpected token {t!r}")


def compile_guard_cpp(expr: str, ctx_schema: dict) -> str:
    """Compile a guard expression to a C++ boolean expression."""
    if not expr or expr.strip() == "":
        return "true"
    tokens = tokenize(expr.strip())
    parser = ExprParserCpp(tokens, ctx_schema)
    result = parser.parse_expr()
    if parser.pos != len(parser.tokens):
        raise ValueError(f"Unconsumed tokens in C++ guard {expr!r}: {parser.tokens[parser.pos:]}")
    return result


def compile_update_expr_cpp(yaml_expr, fname: str, finfo: dict, ctx_schema: dict) -> str:
    """Compile a context update expression to a C++ rvalue expression."""
    ctype = YAML_TO_CPP.get(finfo.get("type", "string"), "std::string")
    unsigned_types = ("uint64_t", "uint32_t", "uint16_t", "uint8_t")
    signed_types = ("int64_t", "int32_t", "int16_t", "int8_t")

    if yaml_expr is None:
        return "std::nullopt"
    if isinstance(yaml_expr, bool):
        return "true" if yaml_expr else "false"
    if isinstance(yaml_expr, int):
        if ctype in unsigned_types or ctype in signed_types:
            return f"static_cast<{ctype}>({yaml_expr})"
        return str(yaml_expr)

    expr = str(yaml_expr).strip()
    if expr == "null":
        return "std::nullopt"
    if expr.lower() == "true":
        return "true"
    if expr.lower() == "false":
        return "false"

    # Plain string literal (no context/payload reference)
    if ctype == "std::string" and not expr.startswith("context.") and not expr.startswith("payload."):
        return f'"{expr}"'

    tokens = tokenize(expr)
    parser = ExprParserCpp(tokens, ctx_schema)
    result = parser.parse_arith()
    if parser.pos != len(parser.tokens):
        raise ValueError(f"Unconsumed tokens in C++ update {yaml_expr!r}")

    if ctype in unsigned_types and result.isdigit():
        return f"static_cast<{ctype}>({result})"
    return result


def _row_uses_payload(row: dict) -> bool:
    """Return True if any effect or guard expression in this row references payload fields."""
    if "payload." in str(row.get("guard") or ""):
        return True
    for eff in row.get("effects", []):
        if eff.get("kind") == "update_context":
            for v in eff.get("args", {}).values():
                if isinstance(v, str) and "payload." in v:
                    return True
    return False


def gen_cpp_payload_struct(fsm: dict, prefix: str) -> str:
    """Generate the {Prefix}FsmPayloads outer struct with per-event inner structs.

    Nesting payload structs inside {Prefix}FsmPayloads avoids name collisions when
    multiple FSMs (e.g. Route and Sor) share the same event vocabulary.
    """
    events_with_payloads = [
        (e["name"], e["payload_schema"])
        for e in fsm["events"]
        if e.get("payload_schema")
    ]
    if not events_with_payloads:
        return ""

    lines = [
        "// ── Payload structs ──────────────────────────────────────────────────────────",
        f"struct {prefix}FsmPayloads {{",
    ]
    for ename, schema in events_with_payloads:
        lines.append(f"  struct {ename}Payload {{")
        for pfname, pfinfo in schema.items():
            ctype = YAML_TO_CPP[pfinfo["type"]]
            camel = snake_to_camel(pfname)
            if pfinfo.get("nullable"):
                lines.append(f"    std::optional<{ctype}> {camel}{{}};")
            else:
                lines.append(f"    {ctype} {camel}{{}};")
        lines.append("  };")
    lines.append("};")
    lines.append("")
    return "\n".join(lines)


CPP_EFFECT_VARIANTS = {
    "publish_fix_message": "PublishFixMessage",
    "schedule_timer": "ScheduleTimer",
    "cancel_timer": "CancelTimer",
    "notify": "Notify",
    "chain_identity_stamp": "ChainIdentityStamp",
}


def gen_cpp_effect_tables(fsm: dict, prefix: str) -> tuple[str, dict[int, str | None]]:
    """Emit one ``inline constexpr`` table per transition that has effects.

    Returns the table source and a map from transition identity to the symbol
    that holds its effects — ``None`` where a transition asks for nothing, so
    the caller emits an empty span rather than a table of length zero.

    Namespace scope rather than function-local statics: a `static constexpr`
    declared inside a `switch` case would need its own block, and the brace
    bookkeeping in the transition emitter is already the fiddliest part of it.
    """
    lines: list[str] = []
    symbols: dict[int, str | None] = {}

    for index, row in enumerate(fsm["transitions"]):
        effects = [e for e in row.get("effects", []) if e["kind"] != "update_context"]
        if not effects:
            symbols[id(row)] = None
            continue

        entries = []
        for position, effect in enumerate(effects):
            kind = effect["kind"]
            args = effect.get("args", {})
            if kind == "publish_event_log":
                entries.append(
                    f'{{{prefix}FsmEffectKind::PublishEventLog, {{}}, "{args.get("event", "")}", {{}}}}'
                )
            elif kind == "emit_event":
                entries.append(
                    f"{{{prefix}FsmEffectKind::EmitEvent, "
                    f'"{args.get("target_fsm", "")}", "{args.get("event", "")}", {{}}}}'
                )
            else:
                variant = CPP_EFFECT_VARIANTS.get(kind)
                if variant is None:
                    raise ValueError(f"Unknown effect kind: {kind!r}")
                arg_symbol = f"k{prefix}FsmEffectArgs{index}_{position}"
                pairs = ", ".join(
                    f'{prefix}FsmEffectArg{{"{k}", "{v}"}}' for k, v in args.items()
                )
                lines.append(
                    f"inline constexpr std::array<{prefix}FsmEffectArg, {len(args)}> "
                    f"{arg_symbol} = {{{{{pairs}}}}};"
                )
                entries.append(
                    f"{{{prefix}FsmEffectKind::{variant}, {{}}, {{}}, {arg_symbol}}}"
                )

        symbol = f"k{prefix}FsmEffects{index}"
        lines.append(
            f"inline constexpr std::array<{prefix}FsmEffect, {len(entries)}> "
            f"{symbol} = {{{{{', '.join(entries)}}}}};"
        )
        symbols[id(row)] = symbol

    if not lines:
        return "", symbols

    header = [
        "// ── Effect tables ────────────────────────────────────────────────────────────",
        "//",
        "// One table per transition that asks for something. `transition` returns a span",
        "// over the matching table, so the effects cost nothing to return and outlive any",
        "// caller.",
    ]
    return "\n".join(header + lines) + "\n", symbols


def gen_cpp_transition_impl(fsm: dict, prefix: str) -> str:
    """Generate the inline transition() function body for a C++ header."""
    ctx_schema = fsm["context_schema"]

    event_payload: dict[str, str] = {
        e["name"]: f"{prefix}FsmPayloads::{e['name']}Payload"
        for e in fsm["events"]
        if e.get("payload_schema")
    }

    from_map: dict[str, list] = {s["name"]: [] for s in fsm["states"]}
    for t in fsm["transitions"]:
        from_map[t["from"]].append(t)

    tables, effect_symbols = gen_cpp_effect_tables(fsm, prefix)

    lines = [
        tables,
        "// ── Transition implementation (inline) ──────────────────────────────────────",
        f"inline {prefix}FsmTransitionResult transition(",
        f"    {prefix}FsmState state,",
        f"    {prefix}FsmEvent event,",
        f"    const {prefix}FsmContext& ctx,",
        f"    [[maybe_unused]] const void* rawPayload = nullptr) noexcept {{",
        f"  switch (state) {{",
    ]

    for sinfo in fsm["states"]:
        sname = sinfo["name"]
        transitions = from_map[sname]
        lines.append(f"  case {prefix}FsmState::{sname}:")
        lines.append("    switch (event) {")

        event_map: dict[str, list] = {}
        for t in transitions:
            event_map.setdefault(t["event"], []).append(t)

        for ename, rows in event_map.items():
            payload_type = event_payload.get(ename)
            all_guarded = all(r.get("guard") for r in rows)

            # Payload pointer — mark [[maybe_unused]] if no row references payload fields
            if payload_type:
                if any(_row_uses_payload(r) for r in rows):
                    p_decl = f"      const auto* p = static_cast<const {payload_type}*>(rawPayload);"
                else:
                    p_decl = f"      [[maybe_unused]] const auto* p = static_cast<const {payload_type}*>(rawPayload);"
            else:
                p_decl = None

            # Case needs braces when it declares variables at case scope
            # (payload ptr, or unguarded updates where auto newCtx lives at case scope)
            has_case_scope_decl = bool(p_decl) or any(
                not r.get("guard") and extract_update_context(r.get("effects", []), ctx_schema)
                for r in rows
            )
            use_braces = has_case_scope_decl

            if use_braces:
                lines.append(f"    case {prefix}FsmEvent::{ename}: {{")
            else:
                lines.append(f"    case {prefix}FsmEvent::{ename}:")

            if p_decl:
                lines.append(p_decl)

            for row in rows:
                guard = row.get("guard")
                to_state = row["to"]
                updates = extract_update_context(row.get("effects", []), ctx_schema)
                return_ctx = "newCtx" if updates else "ctx"

                update_stmts = []
                if updates:
                    update_stmts.append("auto newCtx = ctx;")
                    for field, yaml_expr in updates.items():
                        finfo = ctx_schema.get(field, {"type": "string"})
                        cpp_expr = compile_update_expr_cpp(yaml_expr, field, finfo, ctx_schema)
                        update_stmts.append(f"newCtx.{snake_to_camel(field)} = {cpp_expr};")

                symbol = effect_symbols.get(id(row))
                effects = symbol if symbol else "{}"

                if guard:
                    cpp_guard = compile_guard_cpp(guard, ctx_schema)
                    lines.append(f"      if ({cpp_guard}) {{")
                    for stmt in update_stmts:
                        lines.append(f"        {stmt}")
                    lines.append(
                        f"        return {{{prefix}FsmState::{to_state}, {return_ctx}, {effects}, false}};"
                    )
                    lines.append("      }")
                else:
                    for stmt in update_stmts:
                        lines.append(f"      {stmt}")
                    lines.append(
                        f"      return {{{prefix}FsmState::{to_state}, {return_ctx}, {effects}, false}};"
                    )

            if all_guarded:
                lines.append("      return {state, ctx, {}, true};")

            if use_braces:
                lines.append("    }")

        lines.append("    default:")
        lines.append("      return {state, ctx, {}, true};")
        lines.append("    }")  # end inner switch(event)

    lines.append("  default:")
    lines.append("    return {state, ctx, {}, true};")
    lines.append("  }")  # end outer switch(state)
    lines.append("  return {state, ctx, {}, true};")
    lines.append("}")
    lines.append("")

    return "\n".join(lines)


def gen_cpp_header(fsm: dict) -> str:
    prefix = fsm_class_prefix(fsm["name"])
    states = "\n".join(f"  {s['name']}," for s in fsm["states"])
    events = "\n".join(f"  {e['name']}," for e in fsm["events"])
    state_names = "\n".join(
        f'    case {prefix}FsmState::{s["name"]}: return "{s["name"]}";' for s in fsm["states"]
    )
    event_names = "\n".join(
        f'    case {prefix}FsmEvent::{e["name"]}: return "{e["name"]}";' for e in fsm["events"]
    )
    event_from_name = "\n".join(
        f'  if (name == "{e["name"]}") return {prefix}FsmEvent::{e["name"]};'
        for e in fsm["events"]
    )
    ctx_fields = []
    for fname, finfo in fsm["context_schema"].items():
        ctype = YAML_TO_CPP[finfo["type"]]
        camel = snake_to_camel(fname)
        if finfo.get("nullable"):
            ctx_fields.append(f"  std::optional<{ctype}> {camel}{{}};")
        else:
            ctx_fields.append(f"  {ctype} {camel}{{}};")
    payload_struct = gen_cpp_payload_struct(fsm, prefix)
    transition_impl = gen_cpp_transition_impl(fsm, prefix)
    return CPP_HEADER_TEMPLATE.format(
        source=fsm["name"].lower(),
        prefix=prefix,
        states=states,
        events=events,
        state_names=state_names,
        event_names=event_names,
        event_from_name=event_from_name,
        ctx_fields="\n".join(ctx_fields),
        payload_struct=payload_struct,
        transition_impl=transition_impl,
    )


# ──────────────────────────────────────────────────────────────────────────────
# Main
# ──────────────────────────────────────────────────────────────────────────────



# ──────────────────────────────────────────────────────────────────────────────
# Rust emitter
# ──────────────────────────────────────────────────────────────────────────────
#
# The Rust output differs from Java's and C++'s in one way that is the whole
# point of having it: the transition function matches exhaustively on
# (state, event) with no catch-all over states. Adding a state to the schema
# makes Rust fail to COMPILE until the generator emits its arm — where Java
# would compile and take a default branch at run time, and C++ would too unless
# every switch happened to lack a default.
#
# See 70_concepts/idioms/fsm-state-exhaustiveness.md.

RUST_OUT = REPO / "rust" / "ems-fsm" / "src" / "generated"

YAML_TO_RUST = {
    "u8": "u8",
    "u16": "u16",
    "u32": "u32",
    "u64": "u64",
    "i8": "i8",
    "i16": "i16",
    "i32": "i32",
    "i64": "i64",
    "string": "String",
    "bool": "bool",
    "timestamp": "i64",
    "uuid": "String",
    "figi": "String",
    "lei": "String",
    "currency": "String",
}


def rust_module_name(prefix: str) -> str:
    """`VenueSession` → `venue_session_fsm`. The file name, and the module path."""
    out = []
    for i, ch in enumerate(prefix):
        if ch.isupper() and i > 0:
            out.append("_")
        out.append(ch.lower())
    return "".join(out) + "_fsm"


def rust_variant_name(name: str) -> str:
    """Schema name → Rust variant.

    Both conventions appear in the schemas and each breaks the naive handling of
    the other:

    - states are SCREAMING_SNAKE — `PARTIALLY_FILLED` → `PartiallyFilled`;
      keeping the tail would give `PARTIALLYFILLED`
    - events are already PascalCase — `ValidationPassed` stays as it is;
      `.capitalize()` would give `Validationpassed`

    So a part that is entirely upper-case is treated as a snake segment and
    lower-cased below the first letter; anything else keeps its tail.
    """
    parts = []
    for part in name.split("_"):
        if not part:
            continue
        parts.append(part.capitalize() if part.isupper() else part[:1].upper() + part[1:])
    return "".join(parts)


def rust_field_name(name: str) -> str:
    """Context fields stay snake_case — Rust's convention, and already the YAML's."""
    return name


class ExprParserRust:
    """Rust expression compiler — same DSL grammar as ExprParser, Rust emission.

    Differences from the C++ compiler:
    - context.field  → ctx.field           (snake_case is already the YAML form)
    - payload.field  → p.field
    - null           → None
    - optional compare uses as_deref()/is_none() rather than has_value()
    - string literals compare against &str, so the owned side needs .as_str()
    """

    def __init__(self, tokens: list, ctx_schema: dict):
        self.tokens = tokens
        self.pos = 0
        self.ctx_schema = ctx_schema

    def peek(self):
        return self.tokens[self.pos] if self.pos < len(self.tokens) else None

    def consume(self, kind=None):
        t = self.tokens[self.pos]
        if kind and t.kind != kind:
            raise ValueError(f"Expected {kind}, got {t}")
        self.pos += 1
        return t

    def parse_expr(self) -> str:
        return self.parse_or()

    def parse_or(self) -> str:
        parts = [self.parse_and()]
        while self.peek() and self.peek().kind == "OR":
            self.consume("OR")
            parts.append(self.parse_and())
        return ("(" + " || ".join(parts) + ")") if len(parts) > 1 else parts[0]

    def parse_and(self) -> str:
        parts = [self.parse_cmp()]
        while self.peek() and self.peek().kind == "AND":
            self.consume("AND")
            parts.append(self.parse_cmp())
        return ("(" + " && ".join(parts) + ")") if len(parts) > 1 else parts[0]

    def parse_cmp(self) -> str:
        first_left_tok = self.peek()
        left = self.parse_arith()
        t = self.peek()
        if t and t.kind in ("EQ", "NEQ", "LT", "GT", "LEQ", "GEQ"):
            op_tok = self.consume()
            first_right_tok = self.peek()
            right = self.parse_arith()
            op_map = {"LT": "<", "GT": ">", "LEQ": "<=", "GEQ": ">="}

            # context.nullable_field == null / != null
            if (first_left_tok and first_left_tok.kind == "CONTEXT_FIELD" and
                    first_right_tok and first_right_tok.kind == "NULL"):
                finfo = self.ctx_schema.get(first_left_tok.value, {})
                if finfo.get("nullable"):
                    return f"({left}.is_none())" if op_tok.kind == "EQ" else f"({left}.is_some())"

            # context.string_field == 'literal'
            if (op_tok.kind in ("EQ", "NEQ") and
                    first_left_tok and first_left_tok.kind == "CONTEXT_FIELD" and
                    first_right_tok and first_right_tok.kind == "STRING"):
                finfo = self.ctx_schema.get(first_left_tok.value, {})
                val = first_right_tok.value
                if finfo.get("nullable") and finfo.get("type") == "string":
                    if op_tok.kind == "EQ":
                        return f'({left}.as_deref() == Some("{val}"))'
                    return f'({left}.as_deref() != Some("{val}"))'
                if finfo.get("type") == "string":
                    op = "==" if op_tok.kind == "EQ" else "!="
                    return f'({left}.as_str() {op} "{val}")'

            if op_tok.kind == "EQ":
                return f"({left} == {right})"
            if op_tok.kind == "NEQ":
                return f"({left} != {right})"
            return f"({left} {op_map[op_tok.kind]} {right})"
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
            # `parse_or`/`parse_and` already parenthesise what they build, so
            # wrapping again would emit `((a || b))` — which rustc warns on.
            return inner if inner.startswith("(") and inner.endswith(")") else f"({inner})"
        if t.kind == "NULL":
            self.consume()
            return "None"
        if t.kind == "INT":
            self.consume()
            return t.value
        if t.kind == "STRING":
            self.consume()
            return f'"{t.value}"'
        if t.kind == "CONTEXT_FIELD":
            self.consume()
            return f"ctx.{rust_field_name(t.value)}"
        if t.kind == "PAYLOAD_FIELD":
            self.consume()
            return f"p.{rust_field_name(t.value)}"
        raise ValueError(f"Unexpected token {t!r}")


def _strip_outer_parens(expr: str) -> str:
    """Drop one redundant enclosing paren pair.

    The parser parenthesises defensively, which is free in C++ but makes rustc
    emit `unnecessary_parens` — and the workspace denies warnings. Only strips
    when the outermost pair actually encloses the whole expression.
    """
    if not (expr.startswith("(") and expr.endswith(")")):
        return expr
    depth = 0
    for i, ch in enumerate(expr):
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0 and i != len(expr) - 1:
                return expr
    return expr[1:-1]


def compile_guard_rust(expr: str, ctx_schema: dict) -> str:
    """Compile a guard expression to a Rust boolean expression."""
    if not expr or expr.strip() == "":
        return "true"
    tokens = tokenize(expr.strip())
    parser = ExprParserRust(tokens, ctx_schema)
    result = parser.parse_expr()
    if parser.pos != len(parser.tokens):
        raise ValueError(f"Unconsumed tokens in Rust guard {expr!r}: {parser.tokens[parser.pos:]}")
    return _strip_outer_parens(result)


# Generated code is not hand-maintainable, so it is not held to hand-written
# code's lint standards — the same reasoning ADR 0004 applies to generated Java
# and ErrorProne. These are artifacts of uniform emission or of prose copied
# from the schema, not defects:
#
#   doc_markdown          schema descriptions mention FIX names like
#                         OrderCancelReject; back-ticking them would mean
#                         editing every description in the YAML for Rust's
#                         benefit.
#   match_like_matches_macro, match_same_arms
#                         the emitter produces one arm per state uniformly;
#                         collapsing them by hand is what makes it a generator.
#   needless_pass_by_value, wildcard_imports
#                         signature shape is fixed across all five machines.
RUST_GENERATED_ALLOWS = """// Not formatted by hand, so not formatted by rustfmt either.
//
// `cargo fmt` would reformat this file and the fsm-sync gate step would then
// see a diff against what the generator emits — the two checks would deadlock.
// Emitting rustfmt-formatted output instead would make the byte comparison
// depend on the rustfmt version, which is not pinned. The same reasoning
// Spotless already applies on the Java side via targetExclude("**/generated/**").
#![cfg_attr(rustfmt, rustfmt::skip)]
#![allow(
    clippy::doc_markdown,
    clippy::match_like_matches_macro,
    clippy::match_same_arms,
    clippy::too_many_lines
)]"""


def compile_update_expr_rust(yaml_expr, fname: str, finfo: dict, ctx_schema: dict) -> str:
    """Compile an update_context value to a Rust expression."""
    nullable = finfo.get("nullable", False)
    ftype = finfo.get("type", "string")

    if yaml_expr is None:
        return "None"

    if isinstance(yaml_expr, bool):
        return "true" if yaml_expr else "false"

    if isinstance(yaml_expr, int):
        return f"Some({yaml_expr})" if nullable else str(yaml_expr)

    text = str(yaml_expr)
    looks_like_expr = ("context." in text or "payload." in text
                       or any(op in text for op in ("+", "-", "*", "/")))

    if not looks_like_expr:
        if ftype == "string":
            literal = f'"{text}".to_owned()'
            return f"Some({literal})" if nullable else literal
        return f"Some({text})" if nullable else text

    tokens = tokenize(text)
    parser = ExprParserRust(tokens, ctx_schema)
    compiled = parser.parse_expr()
    if parser.pos != len(parser.tokens):
        raise ValueError(f"Unconsumed tokens in Rust update {text!r}")
    compiled = _strip_outer_parens(compiled)
    if ftype == "string" and not nullable:
        compiled = f"{compiled}.to_owned()" if compiled.startswith('"') else compiled
    return f"Some({compiled})" if nullable else compiled


def gen_rust_payload_structs(fsm: dict, prefix: str) -> str:
    """One struct per payload-carrying event, plus an enum over them.

    C++ takes a `const void*` and casts; that is the sort of thing the Rust port
    exists to avoid. A sum type means `apply` cannot be handed the payload of a
    different event, and the compiler checks it.
    """
    out = []
    for e in fsm["events"]:
        schema = e.get("payload_schema")
        if not schema:
            continue
        fields = []
        for fname, finfo in schema.items():
            rtype = YAML_TO_RUST[finfo["type"]]
            if finfo.get("nullable"):
                rtype = f"Option<{rtype}>"
            fields.append(f"    /// `{fname}` from the schema.\n    pub {rust_field_name(fname)}: {rtype},")
        out.append(
            f"/// Payload carried by [`{prefix}FsmEvent::{rust_variant_name(e['name'])}`].\n"
            f"#[derive(Debug, Clone, PartialEq, Eq, Default)]\n"
            f"pub struct {e['name']}Payload {{\n" + "\n".join(fields) + "\n}"
        )
    carriers = [e["name"] for e in fsm["events"] if e.get("payload_schema")]
    variants = "\n".join(
        f"    /// Payload for [`{prefix}FsmEvent::{rust_variant_name(n)}`].\n    {n}({n}Payload),"
        for n in carriers
    )
    if carriers:
        out.append(
            f"/// Any event payload this machine accepts.\n"
            f"///\n"
            f"/// A sum type rather than the `const void*` the C++ header takes: `apply`\n"
            f"/// cannot be handed the payload of a different event, and the compiler\n"
            f"/// checks it rather than the programmer.\n"
            f"#[derive(Debug, Clone, PartialEq, Eq)]\n"
            f"pub enum {prefix}FsmPayload {{\n{variants}\n}}"
        )
    else:
        out.append(
            f"/// This machine has no event payloads. The type exists so `apply` has a\n"
            f"/// uniform signature across every generated machine.\n"
            f"#[derive(Debug, Clone, PartialEq, Eq)]\n"
            f"pub enum {prefix}FsmPayload {{}}"
        )
    return "\n\n".join(out)


def effect_to_rust(effect: dict, prefix: str) -> str | None:
    """Render one effect as a Rust const expression.

    Every field is ``&'static str`` because every value comes from the YAML and
    is therefore known at compile time. That is what lets a transition's effects
    be a ``&'static [..]`` with no allocation and no lifetime to manage.
    """
    kind = effect["kind"]
    args = effect.get("args", {})

    def arg_slice() -> str:
        parts = ", ".join(f'("{k}", "{v}")' for k, v in args.items())
        return f"&[{parts}]"

    if kind == "update_context":
        # Not an effect the caller sees: the runner applies it to the context.
        return None
    if kind == "publish_event_log":
        return f'{prefix}FsmEffect::PublishEventLog {{ event: "{args.get("event", "")}" }}'
    if kind == "emit_event":
        return (
            f"{prefix}FsmEffect::EmitEvent {{ "
            f'target_fsm: "{args.get("target_fsm", "")}", '
            f'event: "{args.get("event", "")}" }}'
        )
    variant = {
        "publish_fix_message": "PublishFixMessage",
        "schedule_timer": "ScheduleTimer",
        "cancel_timer": "CancelTimer",
        "notify": "Notify",
        "chain_identity_stamp": "ChainIdentityStamp",
    }.get(kind)
    if variant is None:
        raise ValueError(f"Unknown effect kind: {kind!r}")
    return f"{prefix}FsmEffect::{variant}({arg_slice()})"


def rust_effects_expr(effects: list, prefix: str) -> str:
    """The ``&'static [Effect]`` literal for one transition's effects."""
    rendered = [e for e in (effect_to_rust(x, prefix) for x in effects) if e]
    if not rendered:
        return "&[]"
    return "&[" + ", ".join(rendered) + "]"


def gen_rust_effects(prefix: str) -> str:
    """The effect enum for one machine.

    A sum type, so an ``EmitEvent`` carrying a timer's arguments does not
    compile. Java models the same thing as a sealed interface of records; C++
    cannot, and its generated struct leaves the unused fields empty instead —
    see ``70_concepts/idioms/effects-as-static-data.md``.
    """
    return f"""\
/// A side effect a transition asks for, as the schema declares it.
///
/// Every field is `&'static str`: the values come from the YAML, so a
/// transition's effects are compile-time data. `apply` returns
/// `&'static [{prefix}FsmEffect]` — no allocation, and nothing to keep alive.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum {prefix}FsmEffect {{
    /// Cascade an event to another FSM instance.
    EmitEvent {{
        /// The machine the event is for, as the schema names it.
        target_fsm: &'static str,
        /// The event to apply there.
        event: &'static str,
    }},
    /// Append an event-log audit record.
    PublishEventLog {{
        /// The log event name.
        event: &'static str,
    }},
    /// Emit an outbound FIX message.
    PublishFixMessage(&'static [(&'static str, &'static str)]),
    /// Schedule a timer.
    ScheduleTimer(&'static [(&'static str, &'static str)]),
    /// Cancel a pending timer.
    CancelTimer(&'static [(&'static str, &'static str)]),
    /// Notify subscribers.
    Notify(&'static [(&'static str, &'static str)]),
    /// Stamp identity chaining trace fields.
    ChainIdentityStamp(&'static [(&'static str, &'static str)]),
}}
"""


def gen_rust_transition(fsm: dict, prefix: str) -> str:
    """The exhaustive transition function.

    Matches on (state, event) with an arm per state — no catch-all across
    states. That is what makes a schema addition a compile error in Rust.
    """
    ctx_schema = fsm["context_schema"]
    from_map = {s["name"]: [] for s in fsm["states"]}
    for t in fsm["transitions"]:
        from_map[t["from"]].append(t)

    event_payload = {
        e["name"]: f"{e['name']}Payload"
        for e in fsm["events"]
        if e.get("payload_schema")
    }
    uses_any_payload = any(
        t["event"] in event_payload and _row_uses_payload(t) for t in fsm["transitions"]
    )

    lines = [
        f"impl {prefix}FsmState {{",
        "    /// Applies `event`, returning the new state and context.",
        "    ///",
        "    /// The match over states is exhaustive with no catch-all: adding a state",
        "    /// to the schema makes this fail to compile until its arm is generated.",
        "    ///",
        "    /// `payload` carries the event's fields where the schema declares any.",
        "    /// A transition whose guard or update reads a payload field cannot fire",
        "    /// without one, so a missing payload is a no-transition rather than a",
        "    /// panic — malformed input is data, not a defect.",
        "    #[must_use]",
        "    #[allow(clippy::too_many_lines, clippy::match_same_arms)]",
        "    // STUDY: fsm-state-exhaustiveness",
        f"    pub fn apply(",
        "        self,",
        f"        event: {prefix}FsmEvent,",
        f"        ctx: &{prefix}FsmContext,",
        (f"        payload: Option<&{prefix}FsmPayload>," if uses_any_payload
         else f"        _payload: Option<&{prefix}FsmPayload>,"),
        f"    ) -> {prefix}FsmTransitionResult {{",
        "        match self {",
    ]

    for sinfo in fsm["states"]:
        sname = sinfo["name"]
        variant = rust_variant_name(sname)
        rows = from_map[sname]

        if not rows:
            # Terminal, or simply unreachable-by-event. `match event { _ => x }`
            # is just `x`, and clippy says so.
            lines.append(
                f"            Self::{variant} => "
                f"{prefix}FsmTransitionResult::no_transition(self, ctx),"
            )
            continue

        lines.append(f"            Self::{variant} => match event {{")

        event_map = {}
        for t in rows:
            event_map.setdefault(t["event"], []).append(t)

        for ename, erows in event_map.items():
            evariant = rust_variant_name(ename)
            lines.append(f"                {prefix}FsmEvent::{evariant} => {{")

            payload_type = event_payload.get(ename)
            needs_payload = payload_type and any(_row_uses_payload(r) for r in erows)
            if needs_payload:
                lines.append(
                    f"                    let Some({prefix}FsmPayload::{ename}(p)) = payload else {{"
                )
                lines.append(
                    f"                        return {prefix}FsmTransitionResult::no_transition(self, ctx);"
                )
                lines.append("                    };")

            all_guarded = all(r.get("guard") for r in erows)

            for row in erows:
                guard = row.get("guard")
                to_variant = rust_variant_name(row["to"])
                updates = extract_update_context(row.get("effects", []), ctx_schema)
                body = []
                if updates:
                    body.append("let mut next = ctx.clone();")
                    for field, yaml_expr in updates.items():
                        finfo = ctx_schema.get(field, {"type": "string"})
                        rust_expr = compile_update_expr_rust(yaml_expr, field, finfo, ctx_schema)
                        body.append(f"next.{rust_field_name(field)} = {rust_expr};")
                    ret_ctx = "next"
                else:
                    ret_ctx = "ctx.clone()"
                effects = rust_effects_expr(row.get("effects", []), prefix)
                ret = (f"{prefix}FsmTransitionResult {{ new_state: Self::{to_variant}, "
                       f"new_context: {ret_ctx}, effects: {effects}, "
                       f"is_no_transition: false }}")
                if guard:
                    lines.append(f"                    if {compile_guard_rust(guard, ctx_schema)} {{")
                    for stmt in body:
                        lines.append(f"                        {stmt}")
                    lines.append(f"                        return {ret};")
                    lines.append("                    }")
                else:
                    for stmt in body:
                        lines.append(f"                    {stmt}")
                    lines.append(f"                    {ret}")
            if all_guarded:
                # Every row is conditional, so falling past them all is possible.
                lines.append(
                    f"                    {prefix}FsmTransitionResult::no_transition(self, ctx)"
                )
            lines.append("                }")

        lines.append(f"                _ => {prefix}FsmTransitionResult::no_transition(self, ctx),")
        lines.append("            },")

    lines.append("        }")
    lines.append("    }")
    lines.append("}")
    return "\n".join(lines)


def gen_rust_module(fsm: dict) -> str:
    prefix = fsm_class_prefix(fsm["name"])
    source = fsm["name"].lower()

    states = "\n".join(
        f"    /// {s.get('description', s['name'])}\n    {rust_variant_name(s['name'])},"
        for s in fsm["states"]
    )
    state_names = "\n".join(
        f'            Self::{rust_variant_name(s["name"])} => "{s["name"]}",'
        for s in fsm["states"]
    )
    initial = next((s["name"] for s in fsm["states"] if s.get("initial")), fsm["states"][0]["name"])
    terminal_arms = [
        f"            Self::{rust_variant_name(s['name'])} => true,"
        for s in fsm["states"] if s.get("terminal")
    ]
    # `match self { _ => false }` is just `false`, and clippy says so.
    terminals = ("match self {\n" + "\n".join(terminal_arms)
                 + "\n            _ => false,\n        }") if terminal_arms else "false"
    events = "\n".join(
        f"    /// {e.get('description', e['name'])}\n    {rust_variant_name(e['name'])},"
        for e in fsm["events"]
    )
    event_names = "\n".join(
        f'            Self::{rust_variant_name(e["name"])} => "{e["name"]}",'
        for e in fsm["events"]
    )
    event_from_name = "\n".join(
        f'            "{e["name"]}" => Some(Self::{rust_variant_name(e["name"])}),'
        for e in fsm["events"]
    )
    ctx_fields = []
    for fname, finfo in fsm["context_schema"].items():
        rtype = YAML_TO_RUST[finfo["type"]]
        if finfo.get("nullable"):
            rtype = f"Option<{rtype}>"
        ctx_fields.append(f"    /// `{fname}` from the schema.\n    pub {rust_field_name(fname)}: {rtype},")

    payloads = gen_rust_payload_structs(fsm, prefix)
    transition = gen_rust_transition(fsm, prefix)

    return RUST_MODULE_TEMPLATE.format(
        allows=RUST_GENERATED_ALLOWS,
        source=source,
        prefix=prefix,
        states=states,
        state_names=state_names,
        initial=rust_variant_name(initial),
        terminals=terminals,
        events=events,
        event_names=event_names,
        event_from_name=event_from_name,
        ctx_fields="\n".join(ctx_fields),
        effects=gen_rust_effects(prefix),
        payloads=payloads,
        transition=transition,
    )


RUST_MODULE_TEMPLATE = """\
// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: schemas/fsm/{source}.fsm.yaml
// Re-run: python3 tools/codegen/fsm_codegen.py --rust-only
//
// The match in `apply` is exhaustive over states with no catch-all. Adding a
// state to the schema makes this fail to COMPILE until its arm is generated —
// where Java compiles and takes a default branch at run time. That difference
// is the point of the Rust port; see
// 70_concepts/idioms/fsm-state-exhaustiveness.md.
{allows}

/// States of the `{prefix}` state machine.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum {prefix}FsmState {{
{states}
}}

impl {prefix}FsmState {{
    /// The initial state, per the schema.
    #[must_use]
    pub const fn initial() -> Self {{
        Self::{initial}
    }}

    /// The state name as the schema spells it.
    ///
    /// This reaches the output journal, so it must match Java's enum constant
    /// character for character — the conformance gate compares bytes.
    #[must_use]
    pub const fn name(self) -> &'static str {{
        match self {{
{state_names}
        }}
    }}

    /// Whether this state accepts no further transitions.
    #[must_use]
    pub const fn is_terminal(self) -> bool {{
        {terminals}
    }}
}}

/// Events the `{prefix}` state machine accepts.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum {prefix}FsmEvent {{
{events}
}}

impl {prefix}FsmEvent {{
    /// The event name as the schema spells it.
    ///
    /// Event names reach the output journal for the same reason state names do —
    /// the `FsmTransition` events the conformance gate compares carry both — so
    /// this must match Java's enum constant character for character.
    #[must_use]
    pub const fn name(self) -> &'static str {{
        match self {{
{event_names}
        }}
    }}

    /// Parses a schema event name. `None` for anything the schema does not define.
    ///
    /// A journal can carry any string; an unrecognised one is data, not a defect,
    /// so the caller decides what to do rather than being handed a panic.
    #[must_use]
    pub fn from_name(name: &str) -> Option<Self> {{
        match name {{
{event_from_name}
            _ => None,
        }}
    }}
}}

/// Context carried alongside the state.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct {prefix}FsmContext {{
{ctx_fields}
}}

/// The outcome of applying an event.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct {prefix}FsmTransitionResult {{
    /// The state after the event. Unchanged when `is_no_transition`.
    pub new_state: {prefix}FsmState,
    /// The context after the event.
    pub new_context: {prefix}FsmContext,
    /// What the schema asks the caller to do, in the order it declares them.
    ///
    /// Static data, not an owned list: the values are all literals from the
    /// YAML, so there is nothing to allocate and nothing to free. Empty when no
    /// transition matched.
    pub effects: &'static [{prefix}FsmEffect],
    /// True when no transition matched — the event is ignored, not an error.
    pub is_no_transition: bool,
}}

impl {prefix}FsmTransitionResult {{
    /// No rule matched: state and context are unchanged, and nothing is asked for.
    #[must_use]
    pub fn no_transition(state: {prefix}FsmState, ctx: &{prefix}FsmContext) -> Self {{
        Self {{
            new_state: state,
            new_context: ctx.clone(),
            effects: &[],
            is_no_transition: true,
        }}
    }}

    /// The events this transition cascades to another machine, in schema order.
    ///
    /// The common reason to look at effects at all: a route reaching `WORKING`
    /// tells the order machine so. Returning `(target, event)` pairs rather than
    /// the effects themselves keeps the caller from matching on variants it does
    /// not handle.
    pub fn emitted_events(&self) -> impl Iterator<Item = (&'static str, &'static str)> + '_ {{
        self.effects.iter().filter_map(|effect| match effect {{
            {prefix}FsmEffect::EmitEvent {{ target_fsm, event }} => Some((*target_fsm, *event)),
            _ => None,
        }})
    }}
}}

{effects}

{payloads}

{transition}
"""


def main():
    parser = argparse.ArgumentParser(description="FSM YAML → Java/C++ codegen")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--java-only", action="store_true")
    parser.add_argument("--cpp-only", action="store_true")
    parser.add_argument("--rust-only", action="store_true")
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

        only_flags = [args.java_only, args.cpp_only, args.rust_only]
        want_java = args.java_only or not any(only_flags)
        want_cpp = args.cpp_only or not any(only_flags)
        want_rust = args.rust_only or not any(only_flags)

        if want_java:
            written = gen_java_for_fsm(fsm, args.dry_run)
            total_written.extend(written)
            if not args.dry_run:
                for p in written:
                    print(f"  wrote {p}")

        if want_cpp:
            cpp_content = gen_cpp_header(fsm)
            prefix = fsm_class_prefix(name)
            cpp_path = CPP_OUT / f"{prefix.lower()}_fsm.hpp"
            if args.dry_run:
                print(f"  DRY-RUN would write: {cpp_path}")
            else:
                cpp_path.parent.mkdir(parents=True, exist_ok=True)
                cpp_path.write_text(cpp_content)
                print(f"  wrote {cpp_path}")

        if want_rust:
            rust_content = gen_rust_module(fsm)
            prefix = fsm_class_prefix(name)
            rust_path = RUST_OUT / f"{rust_module_name(prefix)}.rs"
            if args.dry_run:
                print(f"  DRY-RUN would write: {rust_path}")
            else:
                rust_path.parent.mkdir(parents=True, exist_ok=True)
                rust_path.write_text(rust_content)
                total_written.append(rust_path)
                print(f"  wrote {rust_path}")

    # The generated module index. Written from the same file list the loop used,
    # so a removed schema removes its module rather than leaving a dangling
    # `mod` that fails to compile.
    if (args.rust_only or not any([args.java_only, args.cpp_only, args.rust_only])) and not args.dry_run:
        mods = []
        for fsm_path in fsm_files:
            with open(fsm_path) as f:
                prefix = fsm_class_prefix(yaml.safe_load(f)["name"])
            mods.append(rust_module_name(prefix))
        index = ["// GENERATED FILE — DO NOT EDIT BY HAND.",
                 "// Re-run: python3 tools/codegen/fsm_codegen.py --rust-only",
                 "",
                 "//! One module per `schemas/fsm/*.fsm.yaml`.",
                 ""]
        for m in mods:
            machine = "".join(part[:1].upper() + part[1:] for part in m.split("_")[:-1])
            index.append(f"/// The `{machine}` state machine.")
            index.append(f"pub mod {m};")
        index_path = RUST_OUT / "mod.rs"
        index_path.parent.mkdir(parents=True, exist_ok=True)
        index_path.write_text("\n".join(index) + "\n")
        print(f"  wrote {index_path}")

    # Write shared files (TransitionResult + @Nullable stub)
    if not args.cpp_only and not args.rust_only and not args.dry_run:
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
