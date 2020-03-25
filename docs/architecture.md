Architecture
============

Event Sourcing
--------------

The state of the akka actors is persisted by Event Sourcing.

What is Event Sourcing ?

>>>
    The basic idea behind Event Sourcing is quite simple. A persistent actor receives a (non-persistent) command
    which is first validated if it can be applied to the current state. Here validation can mean anything, from
    simple inspection of a command message’s fields up to a conversation with several external services, for
    example. If validation succeeds, events are generated from the command, representing the effect of the command.
    These events are then persisted and, after successful persistence, used to change the actor’s state. When the
    persistent actor needs to be recovered, only the persisted events are replayed of which we know that they can
    be successfully applied. In other words, events cannot fail when being replayed to a persistent actor, in
    contrast to commands. Event sourced actors may of course also process commands that do not change application
    state such as query commands for example.
>>>

[Source](https://doc.akka.io/docs/akka/2.5.4/scala/persistence.html)

In the core API, this behavior is abstracted by service components. The service components translate the calls into
command and send them to the actor system. There each command is validated and generate events that are persisted
and applied to update the actors' state.

For example, in the case of a proposal update:

- the `ProposalAPI` receive a PUT HTTP request, and calls the `update` method of the `ProposalServiceComponent`
- the `ProposalServiceComponent`then sends a `ProposeCommand` to the `ProposalActor`
- the `ProposalActor` validates the command and build a `ProposalUpdated` event
- this event is then (`persistAndPublishEvent` method):
-- persisted to Cassandra
-- applied to update the actor state via the `applyEvent` method
-- published to Kafka

The recovery is handled by the `receiveRecover` of the actor which is called on each recovered event.

In order to work properly, Event Sourcing requires that each events are handed in sequence by the same actor. In
order to comply with this constraint in a distributed architecture we use Cluster Sharding. This will be described in
the next session.

Cluster Sharding
----------------

to be filled by @francois