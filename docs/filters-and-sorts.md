# Sorting and filterings used on the operation page

See [the original document](https://docs.google.com/document/d/1MoifGAJW2-mZEDFswftn1pz6xKZs3v1BNEqxW-oRwkU/edit) to see it in French

## All the proposals (default, also called `taggedFirst`):

A score is computed for every proposal as a sum of:
- 50 points if it contains tags
- 10 points if more than one organisation has voted on it
- 5 points if only one organisation has voted on it
- A numbr of points, decaying exponentially, is given according to how recent the proposal is.
  The maximum is 30 points and the points decay of 1/3 every 7 days
- A random number of points, up to 5 points

The proposals are then sorted according to this score, in the descending order.

Implementation is:

```scala
request
    .query(functionScoreQuery().query(request.query.getOrElse(matchAllQuery()))
        .functions(
            WeightScore(50D, Some(existsQuery(ProposalElasticsearchFieldNames.tagId))),
            WeightScore(10D, Some(scriptQuery(s"doc['${ProposalElasticsearchFieldNames.organisationId}'].size() > 1"))),
            WeightScore(5D, Some(scriptQuery(s"doc['${ProposalElasticsearchFieldNames.organisationId}'].size() == 1"))),
            exponentialScore(field = ProposalElasticsearchFieldNames.createdAt, scale = "7d", origin = "now")
                .weight(30D)
                .decay(0.33D),
            randomScore(seed = seed).fieldName("id").weight(5D)
        )
        .scoreMode("sum")
        .boostMode(CombineFunction.Replace)
    )
```

## Popular proposals

This score is used on the home page and on the consultation page.

Proposals are sorted according to the lower confidence bound of the top score,
filtering out the proposals with less than 100 votes.

The top score is calculated using these 5 rates:

- `engagement` rate: (votes `agree` + votes `disagree`) / total votes
- `agreement` rate: votes `agree` / total votes
- `adhesion` rate: (`likeIt` qualifications - `noWay` qualifications) / (`agree` votes + `disagree` votes)
- `realistic` rate: (`doable` qualifications - `impossible` qualifications) / (`agree` votes + `disagree` votes)
- `banality` rate: (`platitudeAgree` qualifications + `platitudeDisagree` qualification) / (`agree` votes + `disagree` votes)

The top score is then calculated as:

`engagement` + `agreement` + `adhesion` + 2 * `realistic` - 2 * `banality`

In order to keep the weights easy-to-understand, the rate are normalized beforehand.

This score is computed a first time using only the votes inside a sequence,
then computed with all the votes, and the mean of them is then used.

Since the votes in sequence appear in both scores, they have a greated weight
in the overall score than the other ones.

To this score, we then substract some confidence margin, to take into consideration
the fact that a score is less precise when we have less votes

The score is computed as followed:
(`topScore` for votes in sequence + `topScore` on all votes) / 2 - confidence adjustment (using votes in sequence)

## The actors proposals

This filter only displays the proposals created by the actors of the consultation.
This filter uses the `author.userType` field.
They are users of type `ORGANISATION` or `PESONALITY`.
There is no particular order, so the proposals will appear as Elasticsearch will choose to sort them.

## Recent proposals

The proposals are sorted by their `createdAt` date, in the descending order.
Since it's possible to change the start date of an operation using the backoffice,
it is possible that some proposals have a creation date before the configured
starting date

## Realistic proposals

For each proposal,a realistism score is computed.
This score will be higher when:
- The number of `doable` qualifications is high
- The number of `impossible` qualifications is low
- The number of `doable` qualifications is close to the numbre of `agree` votes

The proposals are sorted using this score, in the descending order.
The maximum score is 1.
The proposals with a realism score lower than 0.2 will be filtered out,
since they won't be considered as realistic.
The proposals with less than 100 votes will also be filtered out, since the score
will be considered as unreliable.

The score is computed as followed:
(`doable` qualifications - `impossible` qualifications) / (votes `agree` + votes `disagree`)

## Controversy

A score is computed according to how close the `likeIt` and `noWay` qualification counts are.
The proposals are sorted according to this score in the descending order.
The maximum score is 0.5.
The proposals with a score lower than 0.1 will be filtered out, since they are not enough controversial.
Proposals with less than 100 votes will be filtered out, since the score will be considered as not reliable.

The score is computed as followed:
min(`likeIt` qualifications, `noWay` qualifications) / (votes `agree` + votes `disagree`)

## Top proposals by theme

A theme will correspond to a `stake` tag.

For each proposal, we will choose a stake tag:
- Only one `stake` tag -> this tag is selected
- Several `stake` tags -> we chose the one associated to the highest number of proposals
- No `stake` tag -> no tag selected

Proposals are then groupped using this `stake` tag and sorted by `topScore`
and according to its votes:

- `topScore` / 1000 if a proposal has less than 100 votes
- `topScore` else

This allows to have proposals to show sooner and on more tags than if we filtered out the ones with less than 100 votes

[See how the top score is computed](#popular-proposals)
