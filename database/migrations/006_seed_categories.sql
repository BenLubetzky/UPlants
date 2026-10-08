-- 006: starting categories (every post needs one). Edit freely. Safe to re-run.
-- icon = Material Symbols name, so the app can show it with Icons.* / material-icons-extended.

insert into public.categories (name, slug, icon) values
  ('Houseplants',          'houseplants',     'potted_plant'),
  ('Succulents & Cacti',   'succulents',      'filter_vintage'),
  ('Vegetables & Herbs',   'vegetables-herbs','eco'),
  ('Flowers',              'flowers',         'local_florist'),
  ('Trees & Shrubs',       'trees-shrubs',    'park'),
  ('Care Tips',            'care-tips',       'water_drop'),
  ('Pests & Diseases',     'pests-diseases',  'bug_report'),
  ('Plant ID',             'plant-id',        'search')
on conflict (slug) do nothing;
