package ru.itmo.vkr.channels;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.Type;
import ru.itmo.vkr.channels.model.CodeRef;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Small helpers over the JavaParser AST. Annotations and types are matched by simple name: the
 * extractor works on sources without a classpath.
 */
final class Ast {

    private Ast() {
    }

    static Optional<AnnotationExpr> annotation(NodeWithAnnotations<?> node, String simpleName) {
        return node.getAnnotations().stream()
                .filter(a -> simpleName(a.getNameAsString()).equals(simpleName))
                .findFirst();
    }

    static boolean hasAnnotation(NodeWithAnnotations<?> node, String simpleName) {
        return annotation(node, simpleName).isPresent();
    }

    /**
     * Values of the named attributes, array initialisers flattened. {@code "value"} also matches
     * the single-member form {@code @A(x)}.
     */
    static List<Expression> attribute(AnnotationExpr annotation, String... names) {
        List<String> wanted = List.of(names);
        List<Expression> raw = new ArrayList<>();
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            if (wanted.contains("value")) {
                raw.add(single.getMemberValue());
            }
        } else if (annotation instanceof NormalAnnotationExpr normal) {
            normal.getPairs().stream()
                    .filter(pair -> wanted.contains(pair.getNameAsString()))
                    .forEach(pair -> raw.add(pair.getValue()));
        }
        List<Expression> flat = new ArrayList<>();
        for (Expression expression : raw) {
            if (expression instanceof ArrayInitializerExpr array) {
                flat.addAll(array.getValues());
            } else {
                flat.add(expression);
            }
        }
        return flat;
    }

    /**
     * {@code java.util.List<Long>} → {@code List}.
     */
    static String simpleName(String name) {
        int generic = name.indexOf('<');
        String raw = generic >= 0 ? name.substring(0, generic) : name;
        return raw.substring(raw.lastIndexOf('.') + 1).trim();
    }

    static String typeName(Type type) {
        if (type.isClassOrInterfaceType()) {
            return type.asClassOrInterfaceType().getNameAsString();
        }
        if (type.isArrayType()) {
            return typeName(type.asArrayType().getComponentType()) + "[]";
        }
        return type.asString();
    }

    static CallableDeclaration<?> enclosingCallable(Node node) {
        return node.findAncestor(CallableDeclaration.class).orElse(null);
    }

    static TypeDeclaration<?> enclosingType(Node node) {
        return node.findAncestor(TypeDeclaration.class).orElse(null);
    }

    static String className(TypeDeclaration<?> type) {
        return type.getFullyQualifiedName().orElse(type.getNameAsString());
    }

    static CodeRef ref(Node node, Path root) {
        TypeDeclaration<?> type = node instanceof TypeDeclaration<?> t ? t : enclosingType(node);
        CallableDeclaration<?> callable = node instanceof CallableDeclaration<?> c ? c : enclosingCallable(node);
        String file = node.findCompilationUnit()
                .flatMap(CompilationUnit::getStorage)
                .map(storage -> root.relativize(storage.getPath()).toString().replace('\\', '/'))
                .orElse(null);
        int line = node.getBegin().map(position -> position.line).orElse(-1);
        return new CodeRef(file, type == null ? null : className(type),
                callable == null ? null : callable.getNameAsString(), line);
    }
}
