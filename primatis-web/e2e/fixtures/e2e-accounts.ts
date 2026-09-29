
export const e2eAccounts = {
  admin: {
    email: 'admin.e2e@primatis.local',
    password: 'PrimatisE2eAdmin#Local',
  },
  librarian: {
    email: 'librarian.e2e@primatis.local',
    password: 'PrimatisE2eLibrarian#Local',
  },
  member: {
    email: 'member.e2e@primatis.local',
    password: 'PrimatisE2eMember#Local',
    // Déterministe : premier appel à MemberNumberGenerator.generateNext()
    // (nextval member_number_seq) après un Flyway migrate frais — le
    
    memberNumber: 'M000000001',
  },
  
  // Reservation, pour ne jamais dépendre d'un Loan créé par un autre
  
  // déterministe : second appel à generateNext() après "member".
  member2: {
    email: 'member2.e2e@primatis.local',
    password: 'PrimatisE2eMember2#Local',
    memberNumber: 'M000000002',
  },
} as const;
