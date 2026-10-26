import copy, unittest
from analyze_act_grinding import calculate, feature, trajectory, validate, fold

def player(key,country,wins,rr=500,tier='Radiant',**kwargs):
    return dict(player_key=key,country=country,resolved_country=country,wins=wins,rr=rr,tier=tier,
        stats_ambiguous=False,carried_forward=False,**kwargs)

class ActGrindingTest(unittest.TestCase):
    def test_matches_unknown_win_distribution_not_desired_country_target(self):
        rows=[player('a#1','in',70),player('b#2','sg',20),player('c#3','sg',25)]+[player(f'u#{i}',None,20) for i in range(12)]
        before=copy.deepcopy(rows)
        base,_=calculate(rows,'current');wins,_=calculate(rows,'wins')
        self.assertLess(wins['in']['radiant'],base['in']['radiant'])
        self.assertEqual(sum(r['players'] for r in wins.values()),len(rows))
        self.assertEqual(rows,before)
        # A high-win unknown pool can favor the high-win country: no hardcoded country penalty.
        higher=[dict(p,wins=70) if not p['country'] else p for p in rows]
        high,_=calculate(higher,'wins');self.assertGreater(high['in']['radiant'],wins['in']['radiant'])

    def test_history_scope_and_insufficient_observations(self):
        p=player('a#1','in',40,rr=300,tier='Immortal 3',observations=2)
        self.assertEqual(trajectory(p),'insufficient history')
        p.update(observations=3,first_observed='2026-10-20T10:00:00+00:00',last_observed='2026-10-20T12:00:00+00:00',peak_observed_tier=4,first_observed_tier='Radiant')
        self.assertEqual(trajectory(p),'previously higher tier')
        original=feature(p,'history');p['resolved_country']='jp';self.assertEqual(feature(p,'history'),original)
        p['carried_forward']=True;self.assertIsNone(feature(p,'history'))

    def test_recovered_reports_are_known_before_allocation(self):
        rows=[player('known#1','sg',20),dict(player('recovered#1',None,70),resolved_country='in'),player('u#1',None,70)]
        counts,unsupported=calculate(rows,'wins');self.assertGreaterEqual(counts['in']['radiant'],1);self.assertEqual(sum(r['players'] for r in counts.values()),3);self.assertFalse(unsupported)
        self.assertEqual(calculate([player('u#1',None,20)],'wins')[1],{'Radiant':1})

    def test_account_folds_case_insensitive_and_validation_handles_sparse_cells(self):
        self.assertEqual(fold(player('Case#ID','in',40)),fold(player('case#id','in',40)))
        rows=[player(f'known#{i}','sg' if i%2 else 'in',i+1) for i in range(20)]
        self.assertGreaterEqual(validate(rows,'history'),0)

if __name__=='__main__':unittest.main()


