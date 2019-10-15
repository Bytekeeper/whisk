lexer grammar BuildLangLexer;

@header {
package org.whisk.buildlang;
}


IMPORT: 'import';
EXPORT: 'export';
ANON: 'anon';
TRUE: 'true';
FALSE: 'false';

COMMENT: '#' ~[\r\n]* '\r'?  '\n'? -> channel(HIDDEN);
ID : [_a-zA-Z] [_a-zA-Z0-9]* ;             // match lower-case identifiers
WS : [ \t\r\n]+ -> channel(HIDDEN) ; // skip spaces, tabs, newlines
STRING_START: '\'' -> pushMode(STRING);

ASSIGN : '=';
LPAREN : '(';
RPAREN : ')';
COMMA: ',';
LBRACKET: '[';
RBRACKET: ']';
COLON: ':';
QMARK: '?';
DOT: '.';

mode STRING;

ESCAPE_SINGLE_QUOTE : '\\\'' { setText("'"); };
ESCAPE_BACKSLASH : '\\\\' { setText("\\"); };
TEXT: ~[\\'\r\n]+;
STRING_END : '\'' -> popMode;