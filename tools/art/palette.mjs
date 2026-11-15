/**
 *  Preview colours for tools/art (light / dark). The app takes the environment roles from the theme
 *  (`V3ArtPalette`) and the character roles from `ui/theme/ArtInk.kt`; keep these in step for honest previews.
 */
export const LIGHT = {
  bg: '#FFFFFF',
  ground: '#E3E3E6', rock: '#EBEBEC', rock2: '#DEDEE1', leaf: '#569230', leaf2: '#67A23A', shadow: '#EEEEF1', line: '#1E211E',
  skin: '#F6C699', skin2: '#EBB384', blush: '#F3A884', nose: '#ED9366', ink: '#2A1E17', brow: '#3B2416',
  hair: '#523011', hairD: '#2E221A', grey: '#C9C2BA', shoe: '#3B2A20', white: '#FFFFFF',
  orange: '#EF9532', orange2: '#E07B2F', teal: '#3E9EA3', teal2: '#2F8288', red: '#E0574A', red2: '#C64538',
  navy: '#3A4A66', navy2: '#2E3B52', cream: '#F7EFE1', cream2: '#E6D7BD', mustard: '#F2BE45', mustard2: '#DDA52E',
  kraft: '#DDA66B', kraft2: '#C88C52', plum: '#8E5A9B', plum2: '#774985', sky: '#7DB9E8', sky2: '#5E9ED3',
};
export const DARK = {
  ...LIGHT,
  bg: '#141518',
  ground: '#4A4E56', rock: '#30333A', rock2: '#292C32', leaf: '#3F7428', leaf2: '#4C8A31', shadow: '#24262B', line: '#C4C8CF',
};
