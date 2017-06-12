package com.nvlad.yii2support.typeprovider;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.elements.impl.MethodReferenceImpl;
import com.jetbrains.php.lang.psi.elements.impl.VariableImpl;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider3;
import com.nvlad.yii2support.common.ClassUtils;
import org.jetbrains.annotations.Nullable;


import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created by oleg on 2017-06-08.
 */
public class YiiTypeProvider extends CompletionContributor implements PhpTypeProvider3 {
    @Override
    public char getKey() {
        return 'S';
    }

    @Nullable
    @Override
    public PhpType getType(PsiElement psiElement) {
        if (psiElement instanceof MethodReference) {
            MethodReference referenceMethod = (MethodReference)psiElement;
            PhpExpression classReference = ((MethodReferenceImpl) psiElement).getClassReference();
            if (referenceMethod.getName() != null && referenceMethod.getName().equals("createObject")
                    && referenceMethod.getParameters().length > 0) {
                if (classReference != null && classReference.getName() != null && classReference.getName().equals("Yii")) {

                    //System.out.print("getType" + (System.currentTimeMillis() % 1000) + "\n");
                    PhpPsiElement firstParam = (PhpPsiElement) referenceMethod.getParameters()[0];
                    if (firstParam instanceof ArrayCreationExpression) {
                        for (ArrayHashElement elem : ((ArrayCreationExpression) firstParam).getHashElements()) {
                            if (elem.getKey() != null && elem.getKey().getText() != null &&
                                    ClassUtils.removeQuotes(elem.getKey().getText()).equals("class")) {
                                PhpType phpType = getClass(elem.getValue());

                                //System.out.print("getType" + (System.currentTimeMillis() % 1000) + "\n");
                                if (phpType != null)
                                    return new PhpType().add(phpType);
                            }
                        }
                    } else {
                        PhpType phpType = getClass(firstParam);
                        //System.out.print("getType" + (System.currentTimeMillis() % 1000) + "\n");
                        if (phpType != null)
                            return new PhpType().add(phpType);
                    }

                }
            }
        }
        return null;
    }

    private PhpType getClass(PhpPsiElement elem) {
        if (elem instanceof ClassConstantReference) {
            if (elem.getName() != null && elem.getName().equals("class")
                    && ((ClassConstantReference) elem).getClassReference() != null)
                return ((ClassConstantReference) elem).getClassReference().getType();
        }
        if (elem instanceof MethodReference) {
            if (elem.getName() != null && elem.getName().equals("className")
                    && ((MethodReference) elem).getClassReference() != null)
                return ((MethodReference) elem).getClassReference().getType();
        }

        return null;
    }

    @Override
    public Collection<? extends PhpNamedElement> getBySignature(String s, Set<String> set, int i, Project project) {
        return null;
    }
}
