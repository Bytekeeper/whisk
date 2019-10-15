parser grammar BuildLangParser;

options {
    tokenVocab = BuildLangLexer;
}

@header {
package org.whisk.buildlang;
}

ruleDef : ANON? name=ID LPAREN (params+=ruleParamDef (COMMA params+=ruleParamDef)*)? RPAREN (ASSIGN impl=ruleParamValue)?;
qName: name += ID (DOT name += ID)*;
ruleParamDef: name=ID optional=QMARK? (COLON type=typeDef)?;
typeDef: LBRACKET RBRACKET;
goalDef : name=ID ASSIGN value=ruleParamValue;
ruleCall : name=qName LPAREN ( | param = ruleParamValue | params+=ruleParam (COMMA params+=ruleParam)*) RPAREN;
ruleParam : name=ID ASSIGN value=ruleParamValue;
ruleParamValue: (listItem | list);
listItem: qName | string | ruleCall | bool;
list : LBRACKET (items+=listItem (COMMA items+=listItem)*)? RBRACKET;
bool : TRUE | FALSE;
string locals [StringBuilder tmp = new StringBuilder(), String value] : STRING_START (stringPart { $string::tmp.append($stringPart.text); })* STRING_END { $value = $tmp.toString(); };
stringPart: TEXT | ESCAPE_SINGLE_QUOTE | ESCAPE_BACKSLASH;

buildFile: imports? exports? (rules += ruleDef | goals += goalDef)+;
imports: IMPORT packages+=qName (COMMA packages+=qName)*;
exports: EXPORT rules+=qName (COMMA rules+=qName)*;
