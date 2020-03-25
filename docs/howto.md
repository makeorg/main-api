Howto
=====

Update an Event Sourcing event
------------------------------

When you update an event used in Event Sourcing, you must be careful of not breaking
the backward compatibility with old events.

Event sourced events are stored in two places in our architecture:
- in Cassandra, where the actors are persisted
- in Kafka, where the events are kept for 10 years

See [Architecture](architecture.md#event-sourcing)

The backward compatibility in Cassandra is ensured by persistence unit tests `ProposalSerializersTest`.

So if those tests pass, you're OK. Don't forget to add your own sample event with the new format,
to make sure that the next change don't break yours.

The backward compatibility in Kafka is enforced by the Avro Registry. This constraints requires new AVRO schema
to include the old ones.

The current way to check that backward-compatibility is to run the API locally with the production branch.
This should populate the registry and kafka with the current schema. Then shut it down, switch to your branch and
run it on the same infrastructure. If it works, you're OK.

These modifications are known to be backward-compatible:
- adding a field
- making a field optional

If your change is not backward compatible either for Cassandra or Kafka, the solution is to create a new event.
And to keep the support for the old ones.

