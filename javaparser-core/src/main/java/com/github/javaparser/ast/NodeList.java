/*
 * Copyright (C) 2007-2010 Júlio Vilmar Gesser.
 * Copyright (C) 2011, 2013-2026 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */
package com.github.javaparser.ast;

import com.github.javaparser.HasParentNode;
import com.github.javaparser.ast.observer.AstObserver;
import com.github.javaparser.ast.observer.Observable;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.metamodel.InternalProperty;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A list of nodes.
 * It usually has a parent node.
 * Unlike normal Nodes, this does not mean that it is a child of that parent.
 * Instead, this list will make every node it contains a child of its parent.
 * This way, a NodeList does not create an extra level inside the AST.
 *
 * @param <N> the type of nodes contained.
 */
public class NodeList<N extends Node>
        implements List<N>, Iterable<N>, HasParentNode<NodeList<N>>, Visitable, Observable {

    @InternalProperty
    private final List<N> innerList = new ArrayList<>(0);

    private Node parentNode;

    private final List<AstObserver> observers = new ArrayList<>();

    public NodeList() {
        parentNode = null;
    }

    public NodeList(Collection<N> n) {
        this.addAll(n);
    }

    @SafeVarargs
    public NodeList(N... n) {
        this.addAll(Arrays.asList(n));
    }

    @Override
    public boolean add(N node) {
        notifyElementAdded(innerList.size(), node);
        own(node);
        return innerList.add(node);
    }

    private void own(N node) {
        if (node == null) {
            return;
        }
        setAsParentNodeOf(node);
    }

    public boolean remove(Node node) {
        int index = innerList.indexOf(node);
        if (index != -1) {
            notifyElementRemoved(index, node);
            node.setParentNode(null);
        }
        return innerList.remove(node);
    }

    /**
     * Removes and returns the first element of this list.
     * <p>
     * This method conforms to the {@link java.util.Deque} contract:
     * when the list is empty, {@link NoSuchElementException} is thrown
     * instead of letting an internal {@code ArrayIndexOutOfBoundsException} escape.
     *
     * @return the first element of this list
     * @throws NoSuchElementException if this list is empty
     */
    public N removeFirst() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return remove(0);
    }

    /**
     * Removes and returns the last element of this list.
     * <p>
     * This method conforms to the {@link java.util.Deque} contract:
     * when the list is empty, {@link NoSuchElementException} is thrown
     * instead of letting an internal {@code ArrayIndexOutOfBoundsException} escape.
     *
     * @return the last element of this list
     * @throws NoSuchElementException if this list is empty
     */
    public N removeLast() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return remove(innerList.size() - 1);
    }

    @SafeVarargs
    public static <X extends Node> NodeList<X> nodeList(X... nodes) {
        final NodeList<X> nodeList = new NodeList<>();
        Collections.addAll(nodeList, nodes);
        return nodeList;
    }

    public static <X extends Node> NodeList<X> nodeList(Collection<X> nodes) {
        final NodeList<X> nodeList = new NodeList<>();
        nodeList.addAll(nodes);
        return nodeList;
    }

    public static <X extends Node> NodeList<X> nodeList(NodeList<X> nodes) {
        final NodeList<X> nodeList = new NodeList<>();
        nodeList.addAll(nodes);
        return nodeList;
    }

    public boolean contains(N node) {
        return innerList.contains(node);
    }

    @Override
    public int size() {
        return innerList.size();
    }

    @Override
    public N get(int i) {
        return innerList.get(i);
    }

    @Override
    public Iterator<N> iterator() {
        // Custom iterator required, to ensure that the relevant `notifyElement...` methods are called.
        return new NodeListIterator(innerList);
    }

    /**
     * Replaces the element at the specified position in this list with the specified element.
     * <p>
     * This method conforms to the {@link java.util.List} contract: an invalid index
     * ({@code index < 0 || index >= size()}) throws {@link IndexOutOfBoundsException},
     * not {@code IllegalArgumentException}.
     *
     * @param index index of the element to replace
     * @param element element to be stored at the specified position
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException if the index is out of range
     *         ({@code index < 0 || index >= size()})
     * @see java.util.List#set(int, Object)
     */
    @Override
    public N set(int index, N element) {
        if (index < 0 || index >= innerList.size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + innerList.size());
        }
        if (element == innerList.get(index)) {
            return element;
        }
        notifyElementReplaced(index, element);
        innerList.get(index).setParentNode(null);
        setAsParentNodeOf(element);
        return innerList.set(index, element);
    }

    @Override
    public N remove(int index) {
        notifyElementRemoved(index, innerList.get(index));
        N remove = innerList.remove(index);
        if (remove != null) remove.setParentNode(null);
        return remove;
    }

    @Override
    public boolean isEmpty() {
        return innerList.isEmpty();
    }

    @Override
    public void sort(Comparator<? super N> comparator) {
        innerList.sort(comparator);
    }

    public void addAll(NodeList<N> otherList) {
        for (N node : otherList) {
            add(node);
        }
    }

    /**
     * Inserts the specified element at the specified position in this list.
     * Shifts the element currently at that position (if any) and any subsequent
     * elements to the right (adds one to their indices).
     * <p>
     * This method conforms to the {@link java.util.List} contract: an invalid index
     * ({@code index < 0 || index > size()}) throws {@link IndexOutOfBoundsException},
     * not {@code IllegalArgumentException}.
     *
     * @param index index at which the specified element is to be inserted
     * @param node element to be inserted
     * @throws IndexOutOfBoundsException if the index is out of range
     *         ({@code index < 0 || index > size()})
     * @see java.util.List#add(int, Object)
     */
    @Override
    public void add(int index, N node) {
        if (index < 0 || index > innerList.size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + innerList.size());
        }
        notifyElementAdded(index, node);
        own(node);
        innerList.add(index, node);
    }

    /**
     * Inserts the node before all other nodes.
     */
    public NodeList<N> addFirst(N node) {
        add(0, node);
        return this;
    }

    /**
     * Inserts the node after all other nodes. (This is simply an alias for add.)
     */
    public NodeList<N> addLast(N node) {
        add(node);
        return this;
    }

    /**
     * Inserts the node after afterThisNode.
     *
     * @throws IllegalArgumentException when afterThisNode is not in this list.
     */
    public NodeList<N> addAfter(N node, N afterThisNode) {
        int i = indexOf(afterThisNode);
        if (i == -1) {
            throw new IllegalArgumentException("Can't find node to insert after.");
        }
        add(i + 1, node);
        return this;
    }

    /**
     * Inserts the node before beforeThisNode.
     *
     * @throws IllegalArgumentException when beforeThisNode is not in this list.
     */
    public NodeList<N> addBefore(N node, N beforeThisNode) {
        int i = indexOf(beforeThisNode);
        if (i == -1) {
            throw new IllegalArgumentException("Can't find node to insert before.");
        }
        add(i, node);
        return this;
    }

    /**
     * @return the first node, or empty if the list is empty.
     */
    public Optional<N> getFirst() {
        if (isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(get(0));
    }

    /**
     * @return the last node, or empty if the list is empty.
     */
    public Optional<N> getLast() {
        if (isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(get(size() - 1));
    }

    @Override
    public Optional<Node> getParentNode() {
        return Optional.ofNullable(parentNode);
    }

    /**
     * Sets the parentNode
     *
     * @param parentNode the parentNode
     * @return this, the NodeList
     */
    @Override
    public NodeList<N> setParentNode(Node parentNode) {
        this.parentNode = parentNode;
        setAsParentNodeOf(innerList);
        return this;
    }

    @Override
    public Node getParentNodeForChildren() {
        return parentNode;
    }

    @Override
    public <R, A> R accept(final GenericVisitor<R, A> v, final A arg) {
        return v.visit(this, arg);
    }

    @Override
    public <A> void accept(final VoidVisitor<A> v, final A arg) {
        v.visit(this, arg);
    }

    /**
     * @see java.lang.Iterable#forEach(java.util.function.Consumer)
     */
    @Override
    public void forEach(Consumer<? super N> action) {
        innerList.forEach(action);
    }

    /**
     * @see java.util.List#contains(java.lang.Object)
     */
    @Override
    public boolean contains(Object o) {
        return innerList.contains(o);
    }

    /**
     * @see java.util.List#toArray()
     */
    @Override
    public Object[] toArray() {
        return innerList.toArray();
    }

    /**
     * @see java.util.List#toArray(java.lang.Object[])
     */
    @Override
    public <T> T[] toArray(T[] a) {
        return innerList.toArray(a);
    }

    /**
     * @see java.util.List#remove(java.lang.Object)
     */
    @Override
    public boolean remove(Object o) {
        if (o instanceof Node) {
            return remove((Node) o);
        }
        return false;
    }

    /**
     * @see java.util.List#containsAll(java.util.Collection)
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        return innerList.containsAll(c);
    }

    /**
     * @see java.util.List#addAll(java.util.Collection)
     */
    @Override
    public boolean addAll(Collection<? extends N> c) {
        c.forEach(this::add);
        return !c.isEmpty();
    }

    /**
     * Inserts all of the elements in the specified collection into this
     * list, starting at the specified position.
     * <p>
     * This method conforms to the {@link java.util.List} contract: an invalid index
     * ({@code index < 0 || index > size()}) throws {@link IndexOutOfBoundsException},
     * not {@code IllegalArgumentException}. The per-element index validation is
     * delegated to {@link #add(int, Node)}.
     *
     * @param index index at which to insert the first element from the
     *        specified collection
     * @param c collection containing elements to be added to this list
     * @return {@code true} if this list changed as a result of the call
     * @throws IndexOutOfBoundsException if the index is out of range
     *         ({@code index < 0 || index > size()})
     * @see java.util.List#addAll(int, java.util.Collection)
     */
    @Override
    public boolean addAll(int index, Collection<? extends N> c) {
        for (N e : c) {
            add(index++, e);
        }
        return !c.isEmpty();
    }

    /**
     * @see java.util.List#removeAll(java.util.Collection)
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        boolean changed = false;
        for (Object e : c) {
            changed = remove(e) || changed;
        }
        return changed;
    }

    /**
     * @see java.util.List#retainAll(java.util.Collection)
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = false;
        for (Object e : this.stream().filter(it -> !c.contains(it)).toArray()) {
            if (!c.contains(e)) {
                changed = remove(e) || changed;
            }
        }
        return changed;
    }

    /**
     * @see java.util.List#replaceAll(java.util.function.UnaryOperator)
     */
    @Override
    public void replaceAll(UnaryOperator<N> operator) {
        for (int i = 0; i < this.size(); i++) {
            set(i, operator.apply(this.get(i)));
        }
    }

    /**
     * @see java.util.Collection#removeIf(java.util.function.Predicate)
     */
    @Override
    public boolean removeIf(Predicate<? super N> filter) {
        boolean changed = false;
        for (Object e : this.stream().filter(filter).toArray()) {
            changed = remove(e) || changed;
        }
        return changed;
    }

    /**
     * @see java.util.List#clear()
     */
    @Override
    public void clear() {
        while (!isEmpty()) {
            remove(0);
        }
    }

    /**
     * @see java.util.List#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o) {
        return innerList.equals(o);
    }

    /**
     * @see java.util.List#hashCode()
     */
    @Override
    public int hashCode() {
        return innerList.hashCode();
    }

    /**
     * @see java.util.List#indexOf(java.lang.Object)
     */
    @Override
    public int indexOf(Object o) {
        return innerList.indexOf(o);
    }

    /**
     * @see java.util.List#lastIndexOf(java.lang.Object)
     */
    @Override
    public int lastIndexOf(Object o) {
        return innerList.lastIndexOf(o);
    }

    /**
     * @see java.util.List#listIterator()
     */
    @Override
    public ListIterator<N> listIterator() {
        // Custom iterator required, to ensure that the relevant `notifyElement...` methods are called.
        return new NodeListIterator(innerList);
    }

    /**
     * @see java.util.List#listIterator(int)
     */
    @Override
    public ListIterator<N> listIterator(int index) {
        // Custom iterator required, to ensure that the relevant `notifyElement...` methods are called.
        return new NodeListIterator(innerList, index);
    }

    /**
     * @see java.util.Collection#parallelStream()
     */
    @Override
    public Stream<N> parallelStream() {
        return innerList.parallelStream();
    }

    /**
     * @see java.util.List#subList(int, int)
     */
    @Override
    public List<N> subList(int fromIndex, int toIndex) {
        return innerList.subList(fromIndex, toIndex);
    }

    /**
     * @see java.util.List#spliterator()
     */
    @Override
    public Spliterator<N> spliterator() {
        return innerList.spliterator();
    }

    private void notifyElementAdded(int index, Node nodeAddedOrRemoved) {
        this.observers.forEach(o -> o.listChange(this, AstObserver.ListChangeType.ADDITION, index, nodeAddedOrRemoved));
    }

    private void notifyElementRemoved(int index, Node nodeAddedOrRemoved) {
        this.observers.forEach(o -> o.listChange(this, AstObserver.ListChangeType.REMOVAL, index, nodeAddedOrRemoved));
    }

    private void notifyElementReplaced(int index, Node nodeAddedOrRemoved) {
        this.observers.forEach(o -> o.listReplacement(this, index, this.get(index), nodeAddedOrRemoved));
    }

    @Override
    public void unregister(AstObserver observer) {
        this.observers.remove(observer);
    }

    @Override
    public void register(AstObserver observer) {
        if (!this.observers.contains(observer)) {
            this.observers.add(observer);
        }
    }

    @Override
    public boolean isRegistered(AstObserver observer) {
        return this.observers.contains(observer);
    }

    /**
     * Replaces the first node that is equal to "old" with "replacement".
     *
     * @return true if a replacement has happened.
     */
    public boolean replace(N old, N replacement) {
        int i = indexOf(old);
        if (i == -1) {
            return false;
        }
        set(i, replacement);
        return true;
    }

    /**
     * @return the opposite of isEmpty()
     */
    public boolean isNonEmpty() {
        return !isEmpty();
    }

    public void ifNonEmpty(Consumer<? super NodeList<N>> consumer) {
        if (isNonEmpty()) consumer.accept(this);
    }

    public static <T extends Node> Collector<T, NodeList<T>, NodeList<T>> toNodeList() {
        return Collector.of(NodeList::new, NodeList::add, (left, right) -> {
            left.addAll(right);
            return left;
        });
    }

    private void setAsParentNodeOf(List<? extends Node> childNodes) {
        if (childNodes != null) {
            for (HasParentNode current : childNodes) {
                current.setParentNode(getParentNodeForChildren());
            }
        }
    }

    private void setAsParentNodeOf(Node childNode) {
        if (childNode != null) {
            childNode.setParentNode(getParentNodeForChildren());
        }
    }

    @Override
    public String toString() {
        return innerList.stream().map(Node::toString).collect(Collectors.joining(", ", "[", "]"));
    }

    protected class NodeListIterator implements ListIterator<N> {

        ListIterator<N> iterator;

        N current = null;

        // initialize pointer to head of the list for iteration
        public NodeListIterator(List<N> list) {
            iterator = list.listIterator();
        }

        public NodeListIterator(List<N> list, int index) {
            iterator = list.listIterator(index);
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public N next() {
            current = iterator.next();
            return current;
        }

        @Override
        public boolean hasPrevious() {
            return iterator.hasPrevious();
        }

        @Override
        public N previous() {
            current = iterator.previous();
            return current;
        }

        @Override
        public int nextIndex() {
            return iterator.nextIndex();
        }

        @Override
        public int previousIndex() {
            return iterator.previousIndex();
        }

        @Override
        public void remove() {
            int index = innerList.indexOf(current);
            if (index != -1) {
                notifyElementRemoved(index, current);
                current.setParentNode(null);
            }
            iterator.remove();
        }

        /**
         * Replaces the last element returned by {@link #next()} or {@link #previous()}
         * with the specified element.
         * <p>
         * When the cursor has not been advanced (i.e. neither {@code next()} nor
         * {@code previous()} has been called yet, equivalent to {@code cursor == -1}),
         * this method conforms to the same index-based contract as
         * {@link NodeList#set(int, Node)} and throws {@link IndexOutOfBoundsException}.
         *
         * @param n the element with which to replace the last element returned by
         *        {@code next} or {@code previous}
         * @throws IndexOutOfBoundsException if the cursor has not been advanced
         *         (neither {@code next} nor {@code previous} has been called)
         * @see java.util.ListIterator#set(Object)
         * @see NodeList#set(int, Node)
         */
        @Override
        public void set(N n) {
            int index = innerList.indexOf(current);
            if (index < 0) {
                throw new IndexOutOfBoundsException("Cursor has not been advanced: call next() or previous() before set()");
            }
            if (n != innerList.get(index)) {
                notifyElementReplaced(index, n);
                innerList.get(index).setParentNode(null);
                setAsParentNodeOf(n);
                iterator.set(n);
            }
        }

        @Override
        public void add(N n) {
            notifyElementAdded(innerList.size(), n);
            own(n);
            iterator.add(n);
        }

        @Override
        public void forEachRemaining(Consumer<? super N> action) {
            iterator.forEachRemaining(action);
        }
    }
}
